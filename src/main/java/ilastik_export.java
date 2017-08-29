import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ch.systemsx.cisd.base.mdarray.MDByteArray;
import ch.systemsx.cisd.base.mdarray.MDFloatArray;
import ch.systemsx.cisd.base.mdarray.MDShortArray;
import ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants;

import static ch.systemsx.cisd.hdf5.hdf5lib.H5P.H5Pset_chunk;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5P.H5Pclose;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5P.H5Pcreate;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5P.H5Pset_deflate;
import static ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants.H5P_DATASET_CREATE;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5S.H5Screate_simple;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5S.H5Sclose;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5D.H5Dcreate;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5D.H5Dclose;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5F.H5Fcreate;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5F.H5Fclose;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5D.H5Dwrite;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5A.H5Acreate;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5A.H5Awrite;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5A.H5Aclose;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5D.H5Dset_extent;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5D.H5Dget_space;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5S.H5Sselect_all;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5S.H5Sselect_hyperslab;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5S.H5Sget_simple_extent_dims;
import static ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants.H5F_ACC_TRUNC;
import static ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants.H5P_DEFAULT;
import static ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants.H5S_ALL;
import static ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants.H5T_NATIVE_UINT8;
import static ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants.H5T_NATIVE_UINT16;
import static ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants.H5T_NATIVE_FLOAT;
import static ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants.H5T_STD_I8BE;
import static ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants.H5T_STRING;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants.H5T_NATIVE_INT;


import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.io.SaveDialog;
import ij.plugin.filter.PlugInFilter;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.util.ArrayUtil;
import ij.io.FileInfo;
import ij.macro.*;

/**
 * ilastik export
 *
 * A template for exporting HDf5 of either
 * GRAY8, GRAY16, GRAY32 or COLOR_RGB images.
 *
 * @author j.M
 */
public class ilastik_export implements PlugInFilter {
	protected ImagePlus image;
	protected ImageStack stack;

	// image property members
	private int width;
	private int height;
	private int file_id = -1;
	private int dataspace_id = -1;
	private int dataset_id = -1;
	private int dcpl_id = -1 ;
	private int dataspace_id_color = -1;
	private int attribute_id = -1;
	private int dataspace_id_attr = -1;
	private long[] maxdims = { HDF5Constants.H5S_UNLIMITED, HDF5Constants.H5S_UNLIMITED, HDF5Constants.H5S_UNLIMITED, HDF5Constants.H5S_UNLIMITED,HDF5Constants.H5S_UNLIMITED };
	private int memspace = -1;
	// plugin parameters
	public double value;
	public String name;

	/**
	 * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
	 */
	@Override
	public int setup(String arg, ImagePlus imp) {


		image = imp;

		return DOES_8G + DOES_8C + DOES_16 + DOES_32 + DOES_RGB + NO_CHANGES;
	}

	/**
	 * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
	 */
	@Override
	public void run(ImageProcessor ip) {
		String filename;


		int dim = image.getNDimensions();
		if (dim <= 3)
		{
			GenericDialog g = new GenericDialog("Warning");
			g.addMessage("Be careful how you choose your axes order (standart: txyzc).\nScrollabale axes"
					+ " t (frames) and z (slices) can be easily mistaken.\nFor changing their order go to"
					+ " Image -> Properties");
			g.showDialog();
			if (g.wasCanceled()) return;
		}

		GenericDialog gd = new GenericDialog("Save as HDF5");
		gd.addMessage("Choose Compression level (0-9):");
		int compressionLevel = (int)Prefs.get("hdf5writervibez.compressionlevel", 0);
		gd.addNumericField( "compressionlevel", compressionLevel, 0);
		gd.showDialog();
		if (gd.wasCanceled()) return;


		compressionLevel = (int)(gd.getNextNumber());
		SaveDialog sd = new SaveDialog("Save to HDF5 (new or replace)...",image.getShortTitle(),".h5");
		String directory = sd.getDirectory();
		String name = sd.getFileName();
		if (name == null)
			return;
		if (name == "")
			return;
		filename = directory + name;

		int nFrames   = image.getNFrames();
		int nChannels = image.getNChannels();
		int nLevs     = image.getNSlices();
		int nRows     = image.getHeight();
		int nCols     = image.getWidth();
		int slizeSize = nRows*nCols;

//		long[] count = new long[5];
//		long[] start = { 0,0,0,0,0};
//		long[] block = new long[5];
//		long[] stride = new long[5];
		long scrIndex_p1 = 0;
		long destIndex_p1 = 0;


		long[] attrDims = { 2 };

		IJ.log("Export Dimensions: " + String.valueOf(nFrames) + "x" + String.valueOf(nCols) + "x" 
				+ String.valueOf(nRows) + "x" + String.valueOf(nLevs) + "x" + String.valueOf(nChannels));

		long[] chunk_dims = {1, nCols/8, nRows/8, nLevs, nChannels};

		try
		{

			long[] half_Dims = null;
			int[] channelDims = null;
			long[] channel_Dims = null;
			if (nLevs > 1) 
			{
				channelDims = new int[5];
				channelDims[0] = nFrames; //t
				channelDims[1] = nCols; //x
				channelDims[2] = nRows; //y
				channelDims[3] = nLevs ; //z
				channelDims[4] = nChannels;

				channel_Dims = new long[5];
				channel_Dims[0] = nFrames;
				channel_Dims[1] = nCols; //x
				channel_Dims[2] = nRows; //y
				channel_Dims[3] = nLevs ;
				channel_Dims[4] = nChannels;

				half_Dims = new long[5];
				half_Dims[0] = Math.round(nFrames/2);
				half_Dims[1] = nCols; //x
				half_Dims[2] = nRows; //y
				half_Dims[3] = nLevs; //z
				half_Dims[4] = nChannels;

				IJ.log("half_Dim:" + String.valueOf(Math.round(nFrames/2)-1));
			} 
			else 
			{
				channelDims = new int[5];
				channelDims[0] = nFrames; //t
				channelDims[1] = nCols; //x
				channelDims[2] = nRows; //y
				channelDims[3] = 1 ;
				channelDims[4] = nChannels;

				channel_Dims = new long[5];
				channel_Dims[0] = nFrames;
				channel_Dims[1] = nCols; //x
				channel_Dims[2] = nRows; //y
				channel_Dims[3] = 1 ;
				channel_Dims[4] = nChannels;
				
				half_Dims = new long[5];
				half_Dims[0] = Math.round(nFrames/2);
				half_Dims[1] = nCols; //x
				half_Dims[2] = nRows; //y
				half_Dims[3] = 1; //z
				half_Dims[4] = nChannels;
			}
			
			long[] iniDims = new long[5];
			iniDims[0] = 1;
			iniDims[1] = nCols;
			iniDims[2] = nRows;
			iniDims[3] = 1;
			iniDims[4] = 1;
			
			long[] color_iniDims = new long[5];
			color_iniDims[0] = 1;
			color_iniDims[1] = nCols;
			color_iniDims[2] = nRows;
			color_iniDims[3] = 1;
			color_iniDims[4] = 3;

			try {
				file_id = H5Fcreate(filename, H5F_ACC_TRUNC, H5P_DEFAULT, H5P_DEFAULT);
			}
			catch (Exception e) {
				e.printStackTrace();
			}

			try {
				dataspace_id = H5Screate_simple(5, iniDims, maxdims);
			}
			catch (Exception e) {
				e.printStackTrace();
			}


			try{
				dcpl_id = H5Pcreate(H5P_DATASET_CREATE);
			}
			catch (Exception e) {
				e.printStackTrace();
			}

			try{
				H5Pset_chunk(dcpl_id, 5, chunk_dims);
			}
			catch (Exception e) {
				e.printStackTrace();
			}

			try{
				H5Pset_deflate(dcpl_id, compressionLevel);
			}
			catch (Exception e) {
				e.printStackTrace();
			}

			IJ.log("chunksize: " + "1" + "x" + String.valueOf(nCols/8) + "x" + String.valueOf(nRows/8) + "x" +  String.valueOf(nLevs) + "x" 
					+  String.valueOf(nChannels));

			int imgColorType = image.getType();

			if (imgColorType == ImagePlus.GRAY8 
					|| imgColorType == ImagePlus.COLOR_256 ) 
			{	
				byte[] pixels = null;
				stack = image.getStack();

				int timestep = 0;
				int z_axis = 0;
	
				try {
					if ((file_id >= 0) && (dataspace_id >= 0))
						dataset_id =  H5Dcreate(file_id, "exported_data", H5T_NATIVE_UINT8, dataspace_id,
								H5P_DEFAULT, dcpl_id, H5P_DEFAULT);
				}
				catch (Exception e) {
					e.printStackTrace();
				}
				
				
				for (int i=1; i<=stack.getSize();i++){ // stack size: levels*t 500
					
					pixels = (byte[]) stack.getPixels(i);
					byte[] pixels_target = new byte[pixels.length];
					
				
					for (int y=0; y<nRows; y++){
						for(int x=0; x<nCols; x++){
							pixels_target[y + x*(nRows)] = pixels[x + y*(nCols)];
						}
					}
			
					
					if (i== 1){
					
						try {
							if (dataset_id >= 0) 
								H5Dwrite(dataset_id, H5T_NATIVE_UINT8, H5S_ALL, H5S_ALL,
										H5P_DEFAULT, pixels_target);
						}
						catch (Exception e) {
							e.printStackTrace();
						}
						
				        try {
				        	if (dataspace_id >= 0)
				        		H5Sclose(dataspace_id);
				        }
				        catch (Exception e) {
				            e.printStackTrace();
				        }
						z_axis += 1;
					}
					else{
						
						if (i==2){
							
							
							long[] extdims = new long[5];
							extdims = channel_Dims ;
							
							try {
								if (dataset_id >= 0)
									H5Dset_extent(dataset_id, extdims);
							}
							catch (Exception e) {
								e.printStackTrace();
							}

						}
						
						try {
							if (dataspace_id >= 0) {
								
								dataspace_id = H5Dget_space(dataset_id);

								long[] start = {timestep,0,0,z_axis,0};
														
								H5Sselect_hyperslab(dataspace_id, HDF5Constants.H5S_SELECT_SET, start, null, iniDims, null);
								
								memspace = H5Screate_simple(5, iniDims, null);
								
								if (dataset_id >= 0)
									H5Dwrite(dataset_id, H5T_NATIVE_UINT8, memspace, dataspace_id,
											H5P_DEFAULT, pixels_target);
							}
						}
						catch (Exception e) {
							e.printStackTrace();
						}
						
						z_axis += 1;
											
						if ((i % (nLevs*nChannels))==0){
							timestep += 1;
							z_axis = 0;
						}
					}
				}


				IJ.log("write uint8 hdf5");
				IJ.log("compressionLevel: " + String.valueOf(compressionLevel));
				IJ.log("Done");

			}

			else if (imgColorType == ImagePlus.GRAY16) 
			{
				short[] pixels = null;
				stack = image.getStack();


				int timestep = 0;
				int z_axis = 0;
	
				try {
					if ((file_id >= 0) && (dataspace_id >= 0))
						dataset_id =  H5Dcreate(file_id, "exported_data", H5T_NATIVE_UINT16, dataspace_id,
								H5P_DEFAULT, dcpl_id, H5P_DEFAULT);
				}
				catch (Exception e) {
					e.printStackTrace();
				}

				for (int i=1;i<=stack.getSize();i++){
					pixels = (short[]) stack.getPixels(i);
					short[] pixels_target = new short[pixels.length];
					
				
					for (int y=0; y<nRows; y++){
						for(int x=0; x<nCols; x++){
							pixels_target[y + x*(nRows)] = pixels[x + y*(nCols)];
						}
					}
			
					
					if (i== 1){
						
						try {
							if (dataset_id >= 0) 
								H5Dwrite(dataset_id, H5T_NATIVE_UINT16, H5S_ALL, H5S_ALL,
										H5P_DEFAULT, pixels_target);
						}
						catch (Exception e) {
							e.printStackTrace();
						}
						
				        try {
				        	if (dataspace_id >= 0)
				        		H5Sclose(dataspace_id);
				        }
				        catch (Exception e) {
				            e.printStackTrace();
				        }
						z_axis += 1;
					}
					else{
						
						if (i==2){
							
							
							long[] extdims = new long[5];
							extdims = channel_Dims ;
							
							try {
								if (dataset_id >= 0)
									H5Dset_extent(dataset_id, extdims);
							}
							catch (Exception e) {
								e.printStackTrace();
							}

						}
						
						try {
							if (dataspace_id >= 0) {
								
								dataspace_id = H5Dget_space(dataset_id);

								long[] start = {timestep,0,0,z_axis,0};
														
								H5Sselect_hyperslab(dataspace_id, HDF5Constants.H5S_SELECT_SET, start, null, iniDims, null);
								
								memspace = H5Screate_simple(5, iniDims, null);
								
								if (dataset_id >= 0)
									H5Dwrite(dataset_id, H5T_NATIVE_UINT16, memspace, dataspace_id,
											H5P_DEFAULT, pixels_target);
							}
						}
						catch (Exception e) {
							e.printStackTrace();
						}
						
						z_axis += 1;
											
						if ((i % (nLevs*nChannels))==0){
							timestep += 1;
							z_axis = 0;
						}
					}
				}


				IJ.log("write uint16 hdf5");
				IJ.log("compressionLevel: " + String.valueOf(compressionLevel));
				IJ.log("Done");
			}
			else if (imgColorType == ImagePlus.GRAY32)
			{
				float[] pixels = null;
				stack = image.getStack();


				int timestep = 0;
				int z_axis = 0;
	
				try {
					if ((file_id >= 0) && (dataspace_id >= 0))
						dataset_id =  H5Dcreate(file_id, "exported_data", H5T_NATIVE_FLOAT, dataspace_id,
								H5P_DEFAULT, dcpl_id, H5P_DEFAULT);
				}
				catch (Exception e) {
					e.printStackTrace();
				}
				
				for (int i=1;i<=stack.getSize();i++){
					pixels = (float[]) stack.getPixels(i);
					float[] pixels_target = new float[pixels.length];
					
					
					for (int y=0; y<nRows; y++){
						for(int x=0; x<nCols; x++){
							pixels_target[y + x*(nRows)] = pixels[x + y*(nCols)];
						}
					}
			
					
					if (i== 1){
					
						
						try {
							if (dataset_id >= 0) 
								H5Dwrite(dataset_id, H5T_NATIVE_FLOAT, H5S_ALL, H5S_ALL,
										H5P_DEFAULT, pixels_target);
						}
						catch (Exception e) {
							e.printStackTrace();
						}
						
				        try {
				        	if (dataspace_id >= 0)
				        		H5Sclose(dataspace_id);
				        }
				        catch (Exception e) {
				            e.printStackTrace();
				        }
						z_axis += 1;
					}
					else{
						
						if (i==2){
							
							
							long[] extdims = new long[5];
							extdims = channel_Dims ;
							
							try {
								if (dataset_id >= 0)
									H5Dset_extent(dataset_id, extdims);
							}
							catch (Exception e) {
								e.printStackTrace();
							}

						}
						
						try {
							if (dataspace_id >= 0) {
								
								dataspace_id = H5Dget_space(dataset_id);

								long[] start = {timestep,0,0,z_axis,0};
														
								H5Sselect_hyperslab(dataspace_id, HDF5Constants.H5S_SELECT_SET, start, null, iniDims, null);
								
								memspace = H5Screate_simple(5, iniDims, null);
								
								if (dataset_id >= 0)
									H5Dwrite(dataset_id, H5T_NATIVE_FLOAT, memspace, dataspace_id,
											H5P_DEFAULT, pixels_target);
							}
						}
						catch (Exception e) {
							e.printStackTrace();
						}
						
						z_axis += 1;
											
						if ((i % (nLevs*nChannels))==0){
							timestep += 1;
							z_axis = 0;
						}
					}
				}

				IJ.log("write float32 hdf5");
				IJ.log("compressionLevel: " + String.valueOf(compressionLevel));
				IJ.log("Done");	
			} 
			else if (imgColorType == ImagePlus.COLOR_RGB){

				long[] channelDimsRGB = null;
				if (nLevs > 1) 
				{
					channelDimsRGB = new long[5];
					channelDimsRGB[0] = nFrames; //t
					channelDimsRGB[1] = nCols; //x
					channelDimsRGB[2] = nRows; //y
					channelDimsRGB[3] = nLevs ; //z
					channelDimsRGB[4] = 3;

				} 
				else 
				{
					channelDimsRGB = new long[5];
					channelDimsRGB[0] = nFrames; //t
					channelDimsRGB[1] = nCols; //x
					channelDimsRGB[2] = nRows; //y
					channelDimsRGB[3] = 1 ;
					channelDimsRGB[4] = 3;

				}

				try {
					dataspace_id_color = H5Screate_simple(5, color_iniDims, maxdims);
				}
				catch (Exception e) {
					e.printStackTrace();
				}

				stack = image.getStack();
	
				try {
					if ((file_id >= 0) && (dataspace_id >= 0))
						dataset_id =  H5Dcreate(file_id, "exported_data", H5T_NATIVE_UINT8, dataspace_id_color,
								H5P_DEFAULT, dcpl_id, H5P_DEFAULT);
				}
				catch (Exception e) {
					e.printStackTrace();
				}
				
				for (int t=0; t<=nFrames; t++){
					for (int z=0; z<nLevs; z++) {
					
					int stackIndex = image.getStackIndex(1, z + 1, t + 1);
					ColorProcessor cp = (ColorProcessor)(stack.getProcessor(stackIndex));
					byte[] red   = cp.getChannel(1);
					byte[] green = cp.getChannel(2);
					byte[] blue  = cp.getChannel(3);
					
					byte[][] color_target = new byte[3][red.length];
					
					for (int y=0; y<nRows; y++){
						for(int x=0; x<nCols; x++){
							color_target[0][y + x*(nRows)] = red[x + y*(nCols)];
							color_target[2][y + x*(nRows)] = blue[x + y*(nCols)];
							color_target[1][y + x*(nRows)] = green[x + y*(nCols)];
						}
					}
			
				
				        try {
				        	if (dataspace_id >= 0)
				        		H5Sclose(dataspace_id_color);
				        }
				        catch (Exception e) {
				            e.printStackTrace();
				        }
						
						if (z==0 ){
						
							
							try {
								if (dataset_id >= 0)
									H5Dset_extent(dataset_id, channelDimsRGB);
							}
							catch (Exception e) {
								e.printStackTrace();
							}

						}
						
						for (int c=0; c<3; c++){
						
							try {
								if (dataspace_id >= 0) {
									
									dataspace_id = H5Dget_space(dataset_id);
	
									long[] start = {t,0,0,z,c};
															
									H5Sselect_hyperslab(dataspace_id, HDF5Constants.H5S_SELECT_SET, start, null, iniDims, null);
									
									memspace = H5Screate_simple(5, iniDims, null);
									
									if (dataset_id >= 0)
										H5Dwrite(dataset_id, H5T_NATIVE_UINT8, memspace, dataspace_id,
												H5P_DEFAULT, color_target[c]);
								}
							}
							catch (Exception e) {
								e.printStackTrace();
							}
						
						}
						
					}
				}
					
				IJ.log("write uint8 RGB HDF5");
				IJ.log("compressionLevel: " + String.valueOf(compressionLevel));
				IJ.log("Done");

			}
			else {
				IJ.error("Type Not handled yet!");
			}

			try {
				if (attribute_id >= 0)
					H5Aclose(attribute_id);
			}
			catch (Exception e) {
				e.printStackTrace();
			}

			try {
				H5Pclose(dcpl_id);
			}
			catch (Exception e) {
				e.printStackTrace();
			}

			try {
				if (dataspace_id >= 0)    
					H5Sclose(dataspace_id);
			}
			catch (Exception e) {
				e.printStackTrace();
			}

			try {    
				if (dataset_id >= 0)
					H5Dclose(dataset_id);
			}

			catch (Exception e) {
				e.printStackTrace();
			}


			try {
				if (file_id >= 0)
					H5Fclose(file_id);
			}

			catch (Exception e) {
				e.printStackTrace();
			}



		}
		catch (HDF5Exception err) 
		{
			IJ.error("Error while saving '" + filename + "':\n"
					+ err);
		} 
		catch (Exception err) 
		{
			IJ.error("Error while saving '" + filename + "':\n"
					+ err);
		} 
		catch (OutOfMemoryError o) 
		{
			IJ.outOfMemory("Error while saving '" + filename + "'");
		}

	}

	public void showAbout() {
		IJ.showMessage("ilastik Export",
				"a template for export data into hdf5 for ilastik"
				);
	}

	/**
	 * Main method for debugging.
	 *
	 * For debugging, it is convenient to have a method that starts ImageJ, loads an
	 * image and calls the plugin, e.g. after setting breakpoints.
	 *
	 * @param args unused
	 */
	public static void main(String[] args) {
		// set the plugins.dir property to make the plugin appear in the Plugins menu
		Class<?> clazz = ilastik_export.class;
		String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
		String pluginsDir = url.substring("file:".length(), url.length() - clazz.getName().length() - ".class".length());
		System.out.println(pluginsDir);
		System.setProperty("plugins.dir", pluginsDir);

		// start ImageJ
		new ImageJ();

		ImagePlus image = IJ.openImage("/Users/jmassa/Documents/MaMut_project/drosophila/ilastik_export/Raw_Data_0_10.tif");
		image.show();



	}
}