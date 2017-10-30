package org.ilastik.bdvextension;

import java.io.File;

import org.ilastik.bdvextension.DataTypes.DataType;

import bdv.AbstractViewerSetupImgLoader;
import bdv.ViewerImgLoader;
import bdv.ViewerSetupImgLoader;
import bdv.img.cache.CacheArrayLoader;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.hdf5.DimsAndExistence;
import bdv.img.hdf5.Hdf5ImageLoader;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.img.NativeImg;
import net.imglib2.img.basictypeaccess.volatiles.VolatileAccess;
import net.imglib2.img.cell.AbstractCellImg;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.volatiles.VolatileARGBType;

public class Hdf5IlastikImageLoader<T extends NativeType< T >, V extends Volatile< T > & NativeType< V > , A extends VolatileAccess> extends AbstractViewerSetupImgLoader< T, V> implements ViewerImgLoader
{
	private final DataType< T, V, A > dataType;
	
	protected File hdf5File;

	/**
	 * The {@link Hdf5ImageLoader} can be constructed with an existing
	 * {@link IHDF5Reader} which if non-null will be used instead of creating a
	 * new one on {@link #hdf5File}.
	 *
	 * <p>
	 * <em>Note that {@link #close()} will not close the existingHdf5Reader!</em>
	 */
	private final IHDF5Reader hdf5Reader;

	protected IIlastikHDF5Access hdf5Access;

	private final int numScales;

	private final long[] imageDimensions;

	private final int[] blockDimensions;
	
	private final long[] reorderedDims  = new long[ 3 ]; 

	private VolatileGlobalCellCache cache;
	private CacheArrayLoader< A > loader;
	
	public Hdf5IlastikImageLoader(
			final File hdf5File,
			final String dataset,
			final DataType< T, V, A > dataType
	)
	{
		super( dataType.getType(), dataType.getVolatileType() );
		this.dataType = dataType;
		this.numScales = 1;
		
		this.hdf5File = hdf5File;
		this.hdf5Reader = HDF5Factory.openForReading( hdf5File );
		try
		{
			this.hdf5Access = new IlastikHDF5AccessHack( hdf5Reader, dataset );
		}
		catch ( final Exception e )
		{
			e.printStackTrace();
			this.hdf5Access = new IlastikHDF5Access( hdf5Reader, dataset );
		}
		this.loader = dataType.createArrayLoader( hdf5Access );
		DimsAndExistence dimsAndExistence = hdf5Access.getDimsAndExistence(null);
		this.imageDimensions = dimsAndExistence.getDimensions();
		
		this.blockDimensions = new int[]{32,32,32};

		this.cache = new VolatileGlobalCellCache( numScales, 1 );
	}

	/**
	 * (Almost) create a {@link CachedCellImg} backed by the cache.
	 * The created image needs a {@link NativeImg#setLinkedType(net.imglib2.type.Type) linked type} before it can be used.
	 * The type should be either {@link ARGBType} and {@link VolatileARGBType}.
	 */
	protected < T extends NativeType< T > > AbstractCellImg< T, A, ?, ? > prepareCachedImage(
			final int timepointId,
			final int setupId,
			final int level,
			final LoadingStrategy loadingStrategy,
            final T type)
	{
		final long[] dimensions = imageDimensions;
		final int priority = numScales - 1 - level;
		final CacheHints cacheHints = new CacheHints( loadingStrategy, priority, false );
        final CellGrid grid = new CellGrid( dimensions, this.blockDimensions );
        return cache.createImg(grid, timepointId, setupId, level, cacheHints, this.loader, type);
	}

	@Override
	public VolatileGlobalCellCache getCacheControl()
	{
		return cache;
	}

	@Override
	public ViewerSetupImgLoader< ?, ? > getSetupImgLoader( final int setupId )
	{
		return this;
	}

	@Override
	public RandomAccessibleInterval< T > getImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
        return prepareCachedImage(timepointId, 0, level, LoadingStrategy.BLOCKING, dataType.getType());
	}

	@Override
	public RandomAccessibleInterval< V > getVolatileImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
        return prepareCachedImage(timepointId, 0, level, LoadingStrategy.VOLATILE, dataType.getVolatileType());
	}

	public void setCache( final VolatileGlobalCellCache cache )
	{
		this.cache = cache;
	}

	@Override
	public double[][] getMipmapResolutions() {
		double[] resolution = new double[]{1.0, 1.0, 1.0};
		return new double[][]{resolution};
	}

	@Override
	public AffineTransform3D[] getMipmapTransforms() {
		return new AffineTransform3D[]{ new AffineTransform3D() };
	}

	@Override
	public int numMipmapLevels() {
		return 1;
	}
}
