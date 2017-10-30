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

import java.io.File;
import java.io.IOException;

import org.jdom2.Element;

import ch.systemsx.cisd.hdf5.HDF5DataClass;
import ch.systemsx.cisd.hdf5.HDF5DataSetInformation;
import ch.systemsx.cisd.hdf5.HDF5DataTypeInformation;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import ij.IJ;
import mpicbg.spim.data.XmlHelpers;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.ImgLoaderIo;
import mpicbg.spim.data.generic.sequence.XmlIoBasicImgLoader;

@ImgLoaderIo( format = "ilastik.hdf5", type = Hdf5IlastikImageLoader.class )
public class XmlIoIlastikImageLoader implements XmlIoBasicImgLoader< Hdf5IlastikImageLoader >
{
	@Override
	public Element toXml( final Hdf5IlastikImageLoader imgLoader, final File basePath )
	{
		return null;
	}
	
	public DataTypes.DataType<?,?,?> determineDatasetDatatype(final File hdf5File, final String dataset) throws IOException
	{
		IHDF5Reader reader = HDF5Factory.openForReading( hdf5File );
		final HDF5DataSetInformation info = reader.getDataSetInformation( dataset );
		final HDF5DataTypeInformation ti = info.getTypeInformation();

		if ( ti.getDataClass().equals( HDF5DataClass.INTEGER ) )
		{
			switch ( ti.getElementSize() )
			{
			case 1:
				return DataTypes.UnsignedByte;
			case 2:
				return DataTypes.UnsignedShort;
			default:
				throw new IOException( "expected datatype" + ti );
			}
		}
		else if ( ti.getDataClass().equals( HDF5DataClass.FLOAT ) )
		{
			switch ( ti.getElementSize() )
			{
			case 4:
				return DataTypes.Float;
			default:
				throw new IOException( "expected datatype" + ti );
			}
		}
		return null;
	}

	@Override
	public Hdf5IlastikImageLoader fromXml( final Element elem, final File basePath, final AbstractSequenceDescription< ?, ?, ? > sequenceDescription )
	{
		final String path = XmlHelpers.loadPath( elem, "hdf5", basePath ).toString();
		final String dataset = XmlHelpers.getText(elem, "dataset");
		try{
			DataTypes.DataType<?,?,?> dataType = determineDatasetDatatype(new File( path ), dataset);
			return new Hdf5IlastikImageLoader( new File( path ), dataset, dataType );
		}
		catch( final IOException e )
		{
			IJ.log("Was not able to determine dataset type of dataset \"" + dataset + "\" in \"" + path + "\"");
			return null;
		}
	}
}
