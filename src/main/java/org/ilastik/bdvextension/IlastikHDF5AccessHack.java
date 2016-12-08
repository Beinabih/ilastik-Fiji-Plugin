package org.ilastik.bdvextension;

import static bdv.img.hdf5.Util.reorder;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5D.H5Dclose;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5D.H5Dget_space;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5D.H5Dopen;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5D.H5Dread;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5S.H5Sclose;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5S.H5Screate_simple;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5S.H5Sget_simple_extent_dims;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5S.H5Sselect_hyperslab;
import static ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants.H5P_DEFAULT;
import static ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants.H5S_MAX_RANK;
import static ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants.H5S_SELECT_SET;
import static ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants.H5T_NATIVE_FLOAT;
import static ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants.H5T_NATIVE_UCHAR;
import static ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants.H5T_NATIVE_USHORT;

import java.lang.reflect.Field;

import bdv.img.hdf5.DimsAndExistence;
import bdv.img.hdf5.Util;
import bdv.img.hdf5.ViewLevelId;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import ij.IJ;

/**
 * Access chunked data-sets through lower-level HDF5. This avoids opening and
 * closing the dataset for each chunk when accessing through jhdf5 (This is a
 * huge bottleneck when accessing many small chunks).
 *
 * The HDF5 fileId is extracted from a jhdf5 HDF5Reader using reflection to
 * avoid having to do everything ourselves.
 *
 * @author Tobias Pietzsch &lt;tobias.pietzsch@gmail.com&gt;
 * Adjusted for the ilastik HDF5 format by Carsten Haubold
 */
class IlastikHDF5AccessHack implements IIlastikHDF5Access
{
	private final IHDF5Reader hdf5Reader;

	private final int fileId;

	private final int numericConversionXferPropertyListID;

	private final long[] reorderedDimensions = new long[ 3 ];

	private final long[] reorderedMin = new long[ 3 ];

	private long numTimesteps;
	
	private long numChannels;
	
	private class OpenDataSet
	{
		final int dataSetId;

		final int fileSpaceId;

		public OpenDataSet( final String datasetName )
		{
			dataSetId = H5Dopen( fileId, datasetName, H5P_DEFAULT );
			fileSpaceId = H5Dget_space( dataSetId );
		}

		public void close()
		{
			H5Sclose( fileSpaceId );
			H5Dclose( dataSetId );
		}
	}
	
	private final OpenDataSet openDataSet;

	public IlastikHDF5AccessHack( final IHDF5Reader hdf5Reader, final String dataset ) throws ClassNotFoundException, SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException
	{
		this.hdf5Reader = hdf5Reader;

		final Class< ? > k = Class.forName( "ch.systemsx.cisd.hdf5.HDF5Reader" );
		final Field f = k.getDeclaredField( "baseReader" );
		f.setAccessible( true );
		final Object baseReader = f.get( hdf5Reader );

		final Class< ? > k2 = Class.forName( "ch.systemsx.cisd.hdf5.HDF5BaseReader" );
		final Field f2 = k2.getDeclaredField( "fileId" );
		f2.setAccessible( true );
		fileId = ( ( Integer ) f2.get( baseReader ) ).intValue();

		final Field f3 = k2.getDeclaredField( "h5" );
		f3.setAccessible( true );
		final Object h5 = f3.get( baseReader );

		final Class< ? > k4 = Class.forName( "ch.systemsx.cisd.hdf5.HDF5" );
		final Field f4 = k4.getDeclaredField( "numericConversionXferPropertyListID" );
		f4.setAccessible( true );
		numericConversionXferPropertyListID = ( ( Integer ) f4.get( h5 ) ).intValue();

		openDataSet = new OpenDataSet(dataset);
		
		long[] dimensions = extract5Dimensions();
//		IJ.log("Found dataset of size [" + String.valueOf(dimensions[0]) + ", " + String.valueOf(dimensions[1]) 
//			   + ", " + String.valueOf(dimensions[2]) + ", " + String.valueOf(dimensions[3]) + ", " + String.valueOf(dimensions[4]) + ", ");
		if( dimensions != null )
		{
			this.numTimesteps = dimensions[0];
			this.numChannels = dimensions[4];
		}
		else
		{
			this.numTimesteps = 0;
			this.numChannels = 0;
		}
	}

	private long[] extract5Dimensions()
	{
		final long[] realDimensions = new long[ 5 ];
		try
		{
			final long[] dimensions = new long[ H5S_MAX_RANK ];
			final long[] maxDimensions = new long[ H5S_MAX_RANK ];
			final int rank = H5Sget_simple_extent_dims( openDataSet.fileSpaceId, dimensions, maxDimensions );
			if(rank != 5)
			{
				IJ.log("Found wrong number of axes: " + String.valueOf(rank));
			}
			System.arraycopy( dimensions, 0, realDimensions, 0, rank );
			return realDimensions;
		}
		catch ( final Exception e )
		{}
		return null;
	}
	
	private long[] get5DimsFrom3Dims(final long[] dimensions)
	{
		return new long[]{1, dimensions[0], dimensions[1], dimensions[2], 1};
	}
	
	private long[] get5DMinsFrom3Mins(final long min[], final int timepoint, final int setup)
	{
		int clampedTimepoint = timepoint;
		if(timepoint >= this.numTimesteps)
			clampedTimepoint = (int)this.numTimesteps - 1;
		else if(timepoint < 0)
			clampedTimepoint = 0;
		
		int clampedSetup = setup;
		if(setup >= this.numChannels)
			clampedSetup = (int)this.numChannels - 1;
		else if(setup < 0)
			clampedSetup = 0;
		
		return new long[]{clampedTimepoint, min[0], min[1], min[2], clampedSetup};
	}
	
	@Override
	public synchronized DimsAndExistence getDimsAndExistence( final ViewLevelId id )
	{
		long[] all5Dimensions = extract5Dimensions();
		if ( all5Dimensions != null )
		{
			long[] dimensions = new long[]{all5Dimensions[1], all5Dimensions[2], all5Dimensions[3]};
			return new DimsAndExistence( reorder( dimensions ), true );
		}
		else
			return new DimsAndExistence( new long[] { 1, 1, 1 }, false );
	}

	@Override
	public synchronized short[] readShortMDArrayBlockWithOffset( final int timepoint, final int setup, final int level, final int[] dimensions, final long[] min ) throws InterruptedException
	{
		final short[] dataBlock = new short[ dimensions[ 0 ] * dimensions[ 1 ] * dimensions[ 2 ] ];
		readShortMDArrayBlockWithOffset( timepoint, setup, level, dimensions, min, dataBlock );
		return dataBlock;
	}

	@Override
	public synchronized short[] readShortMDArrayBlockWithOffset( final int timepoint, final int setup, final int level, final int[] dimensions, final long[] min, final short[] dataBlock ) throws InterruptedException
	{
		if ( Thread.interrupted() )
			throw new InterruptedException();
		Util.reorder( dimensions, reorderedDimensions );
		Util.reorder( min, reorderedMin );
		long[] useDims = get5DimsFrom3Dims(reorderedDimensions);
		long[] useMins = get5DMinsFrom3Mins(reorderedMin, timepoint, setup);

		final int memorySpaceId = H5Screate_simple( useDims.length, useDims, null );
		H5Sselect_hyperslab( openDataSet.fileSpaceId, H5S_SELECT_SET, useMins, null, useDims, null );
		H5Dread( openDataSet.dataSetId, H5T_NATIVE_USHORT, memorySpaceId, openDataSet.fileSpaceId, numericConversionXferPropertyListID, dataBlock );
		H5Sclose( memorySpaceId );

		return dataBlock;
	}

	@Override
	public void closeAllDataSets()
	{
		openDataSet.close();
	}

	@Override
	public void close()
	{
		closeAllDataSets();
		hdf5Reader.close();
	}

	@Override
	public synchronized byte[] readByteMDArrayBlockWithOffset(int timepoint, int setup, int level, int[] dimensions, long[] min) throws InterruptedException {
		final byte[] dataBlock = new byte[ dimensions[ 0 ] * dimensions[ 1 ] * dimensions[ 2 ] ];
		readByteMDArrayBlockWithOffset( timepoint, setup, level, dimensions, min, dataBlock );
		return dataBlock;
	}

	@Override
	public synchronized byte[] readByteMDArrayBlockWithOffset(int timepoint, int setup, int level, int[] dimensions, long[] min, byte[] dataBlock) throws InterruptedException {
		if ( Thread.interrupted() )
			throw new InterruptedException();
		Util.reorder( dimensions, reorderedDimensions );
		Util.reorder( min, reorderedMin );
		long[] useDims = get5DimsFrom3Dims(reorderedDimensions);
		long[] useMins = get5DMinsFrom3Mins(reorderedMin, timepoint, setup);

		final int memorySpaceId = H5Screate_simple( useDims.length, useDims, null );
		H5Sselect_hyperslab( openDataSet.fileSpaceId, H5S_SELECT_SET, useMins, null, useDims, null );
		H5Dread( openDataSet.dataSetId, H5T_NATIVE_UCHAR, memorySpaceId, openDataSet.fileSpaceId, numericConversionXferPropertyListID, dataBlock );
		H5Sclose( memorySpaceId );

		return dataBlock;
	}

	@Override
	public synchronized float[] readFloatMDArrayBlockWithOffset(int timepoint, int setup, int level, int[] dimensions, long[] min) throws InterruptedException {
		final float[] dataBlock = new float[ dimensions[ 0 ] * dimensions[ 1 ] * dimensions[ 2 ] ];
		readFloatMDArrayBlockWithOffset( timepoint, setup, level, dimensions, min, dataBlock );
		return dataBlock;
	}

	@Override
	public synchronized float[] readFloatMDArrayBlockWithOffset(int timepoint, int setup, int level, int[] dimensions, long[] min, float[] dataBlock) throws InterruptedException {
		if ( Thread.interrupted() )
			throw new InterruptedException();
		Util.reorder( dimensions, reorderedDimensions );
		Util.reorder( min, reorderedMin );
		long[] useDims = get5DimsFrom3Dims(reorderedDimensions);
		long[] useMins = get5DMinsFrom3Mins(reorderedMin, timepoint, setup);

		final int memorySpaceId = H5Screate_simple( useDims.length, useDims, null );
		H5Sselect_hyperslab( openDataSet.fileSpaceId, H5S_SELECT_SET, useMins, null, useDims, null );
		H5Dread( openDataSet.dataSetId, H5T_NATIVE_FLOAT, memorySpaceId, openDataSet.fileSpaceId, numericConversionXferPropertyListID, dataBlock );
		H5Sclose( memorySpaceId );

		return dataBlock;
	}
}
