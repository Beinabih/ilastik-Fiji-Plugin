/*
 * #%L
 * BigDataViewer core classes with minimal dependencies
 * %%
 * Copyright (C) 2012 - 2016 Tobias Pietzsch, Stephan Saalfeld, Stephan Preibisch,
 * Jean-Yves Tinevez, HongKee Moon, Johannes Schindelin, Curtis Rueden, John Bogovic
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package org.ilastik.bdvextension;

import bdv.img.hdf5.Util;

import bdv.img.hdf5.DimsAndExistence;
import bdv.img.hdf5.Util;
import bdv.img.hdf5.ViewLevelId;
import ch.systemsx.cisd.base.mdarray.MDFloatArray;
import ch.systemsx.cisd.base.mdarray.MDByteArray;
import ch.systemsx.cisd.base.mdarray.MDShortArray;
import ch.systemsx.cisd.hdf5.HDF5DataSetInformation;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import ij.IJ;

class IlastikHDF5Access implements IIlastikHDF5Access
{
	private final IHDF5Reader hdf5Reader;

	private final int[] reorderedDimensions = new int[ 3 ];

	private final long[] reorderedMin = new long[ 3 ];
	
	private final String dataset;
	
	private long numTimesteps;
	
	private long numChannels;

	public IlastikHDF5Access( final IHDF5Reader hdf5Reader, final String dataset )
	{
		this.hdf5Reader = hdf5Reader;
		this.dataset = dataset;
		
		long[] dimensions = extract5Dimensions();
//		IJ.log("Found dataset of size [" + String.valueOf(dimensions[0]) + ", " + String.valueOf(dimensions[1]) 
//		+ ", " + String.valueOf(dimensions[2]) + ", " + String.valueOf(dimensions[3]) + ", " + String.valueOf(dimensions[4]) + ", ");
		
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
		long[] dimensions = null;
		try
		{
			HDF5DataSetInformation info = hdf5Reader.getDataSetInformation( dataset );
			dimensions = info.getDimensions();
		}
		catch ( final Exception e )
		{}
		return dimensions;
	}

	public synchronized DimsAndExistence getDimsAndExistence()
	{
		long[] all5Dimensions = extract5Dimensions();
		if ( all5Dimensions != null )
		{
			long[] dimensions = new long[]{all5Dimensions[1], all5Dimensions[2], all5Dimensions[3]};
			return new DimsAndExistence(Util.reorder(dimensions), true );//reorder
		}
		else
			return new DimsAndExistence( new long[] { 1, 1, 1 }, false );
	}
	
	@Override
	public synchronized DimsAndExistence getDimsAndExistence( final ViewLevelId id )
	{
		return getDimsAndExistence();
	}
	
	private int[] get5DimsFrom3Dims(final int[] dimensions)
	{
		return new int[]{1, dimensions[0], dimensions[1], dimensions[2], 1};
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
	public synchronized short[] readShortMDArrayBlockWithOffset( final int timepoint, final int setup, final int level, final int[] dimensions, final long[] min ) throws InterruptedException
	{
		if ( Thread.interrupted() )
			throw new InterruptedException();
		
		Util.reorder( dimensions, reorderedDimensions );
		Util.reorder( min, reorderedMin );
		final MDShortArray array = hdf5Reader.uint16().readMDArrayBlockWithOffset( this.dataset, get5DimsFrom3Dims(dimensions), get5DMinsFrom3Mins(min, timepoint, setup) );
		return array.getAsFlatArray();
	}

	@Override
	public synchronized short[] readShortMDArrayBlockWithOffset( final int timepoint, final int setup, final int level, final int[] dimensions, final long[] min, final short[] dataBlock ) throws InterruptedException
	{
		System.arraycopy( readShortMDArrayBlockWithOffset( timepoint, setup, level, dimensions, min ), 0, dataBlock, 0, dataBlock.length );
		return dataBlock;
	}
	
	@Override
	public synchronized byte[] readByteMDArrayBlockWithOffset( final int timepoint, final int setup, final int level, final int[] dimensions, final long[] min) throws InterruptedException
	{
		if ( Thread.interrupted() )
			throw new InterruptedException();
		
		Util.reorder( dimensions, reorderedDimensions );
		Util.reorder( min, reorderedMin );
		final MDByteArray array = hdf5Reader.uint8().readMDArrayBlockWithOffset( this.dataset, get5DimsFrom3Dims(reorderedDimensions), get5DMinsFrom3Mins(reorderedMin, timepoint, setup) );
		return array.getAsFlatArray();
	}

	@Override
	public synchronized byte[] readByteMDArrayBlockWithOffset( final int timepoint, final int setup, final int level, final int[] dimensions, final long[] min, final byte[] dataBlock  ) throws InterruptedException
	{
		System.arraycopy( readByteMDArrayBlockWithOffset( timepoint, setup, level, dimensions, min ), 0, dataBlock, 0, dataBlock.length );
		return dataBlock;
	}
	
	@Override
	public synchronized float[] readFloatMDArrayBlockWithOffset( final int timepoint, final int setup, final int level, final int[] dimensions, final long[] min ) throws InterruptedException
	{
		if ( Thread.interrupted() )
			throw new InterruptedException();
		
		Util.reorder( dimensions, reorderedDimensions );
		Util.reorder( min, reorderedMin );
		final MDFloatArray array = hdf5Reader.float32().readMDArrayBlockWithOffset( this.dataset, get5DimsFrom3Dims(reorderedDimensions), get5DMinsFrom3Mins(reorderedMin, timepoint, setup) );
		return array.getAsFlatArray();
	}

	@Override
	public synchronized float[] readFloatMDArrayBlockWithOffset( final int timepoint, final int setup, final int level, final int[] dimensions, final long[] min, final float[] dataBlock ) throws InterruptedException
	{

		System.arraycopy( readFloatMDArrayBlockWithOffset( timepoint, setup, level, dimensions , min ), 0, dataBlock, 0, dataBlock.length );
		return dataBlock;
	}

	@Override
	public void closeAllDataSets()
	{}

	@Override
	public synchronized void close()
	{
		closeAllDataSets();
		hdf5Reader.close();
	}
	
	public static long[] intToLong( final int[] in, final long[] out )
	{
		assert in.length == out.length;
		for ( int i = 0; i < in.length; ++i)
			out[ i ] = in[ i ];
		return out;
	}
}
