import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ch.systemsx.cisd.base.mdarray.MDByteArray;
import ch.systemsx.cisd.base.mdarray.MDFloatArray;
import ch.systemsx.cisd.base.mdarray.MDShortArray;
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
import static ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants.H5F_ACC_TRUNC;
import static ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants.H5P_DEFAULT;
import static ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants.H5S_ALL;
import static ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants.H5T_NATIVE_UINT8;
import static ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants.H5T_NATIVE_UINT16;
import static ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants.H5T_NATIVE_FLOAT;


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

		IJ.log("Export Dimensions: " + String.valueOf(nFrames) + "x" + String.valueOf(nCols) + "x" 
				+ String.valueOf(nRows) + "x" + String.valueOf(nLevs) + "x" + String.valueOf(nChannels));

		long[] chunk_dims = {1, nCols/8, nRows/8, nLevs, nChannels};

		try
		{

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
			}

			try {
				file_id = H5Fcreate(filename, H5F_ACC_TRUNC, H5P_DEFAULT, H5P_DEFAULT);
			}
			catch (Exception e) {
				e.printStackTrace();
			}

			try {
				dataspace_id = H5Screate_simple(5, channel_Dims, null);
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

				MDByteArray arr = new MDByteArray(channelDims);
				byte[] flatArray = arr.getAsFlatArray();
				byte[] flatArray2 = new byte[flatArray.length];


				for (int i=1;i<=stack.getSize();i++){ // stack size: levels*t 500
					pixels = (byte[]) stack.getPixels(i); 

					System.arraycopy(pixels, 0,
							flatArray, (i-1)*slizeSize, slizeSize); 	 		
				}	

				System.arraycopy(flatArray, 0, flatArray2, 0, flatArray.length);
				for (int t=0; t<nFrames; t++) {
					for (int z=0; z<nLevs; z++) {
						for (int y=0; y<nRows; y++) {
							for (int x=0; x<nCols; x++) {
								int scrIndex = x + y*nCols + z*nCols*nRows + t*nCols*nRows*nLevs;
								int destIndex =  z + y*nLevs + x*nLevs*nRows + t*nLevs*nCols*nRows;
								flatArray[destIndex] = flatArray2[scrIndex];
							}
						}
					}
				}

				try {
					if ((file_id >= 0) && (dataspace_id >= 0))
						dataset_id =  H5Dcreate(file_id, "exported_data", H5T_NATIVE_UINT8, dataspace_id,
								H5P_DEFAULT, dcpl_id, H5P_DEFAULT);
				}
				catch (Exception e) {
					e.printStackTrace();
				}

				try {
					if (dataset_id >= 0) 
						H5Dwrite(dataset_id, H5T_NATIVE_UINT8, H5S_ALL, H5S_ALL,
								H5P_DEFAULT, flatArray);
				}
				catch (Exception e) {
					e.printStackTrace();
				}

				IJ.log("write uint8 hdf5");
				IJ.log("compressionLevel: " + String.valueOf(compressionLevel));
				IJ.log("Done");

			}

			else if (imgColorType == ImagePlus.GRAY16) 
			{
				short[] pixels = null;
				stack = image.getStack();

				MDShortArray arr = new MDShortArray(channelDims);
				short[] flatArray = arr.getAsFlatArray();
				short[] flatArray2 = new short[flatArray.length];

				for (int i=1;i<=stack.getSize();i++){
					pixels = (short[]) stack.getPixels(i);

					System.arraycopy(pixels, 0,
							flatArray, (i-1)*slizeSize, slizeSize);
				}

				System.arraycopy(flatArray, 0, flatArray2, 0, flatArray.length);
				for (int t=0; t<nFrames; t++) {
					for (int z=0; z<nLevs; z++) {
						for (int y=0; y<nRows; y++) {
							for (int x=0; x<nCols; x++) {
								int scrIndex = x + y*nCols + z*nCols*nRows + t*nCols*nRows*nLevs;
								int destIndex =  z + y*nLevs + x*nLevs*nRows + t*nLevs*nCols*nRows;
								flatArray[destIndex] = flatArray2[scrIndex];
							}
						}
					}
				}
				
				try {
					if ((file_id >= 0) && (dataspace_id >= 0))
						dataset_id =  H5Dcreate(file_id, "exported_data", H5T_NATIVE_UINT16, dataspace_id,
								H5P_DEFAULT, dcpl_id, H5P_DEFAULT);
				}
				catch (Exception e) {
					e.printStackTrace();
				}

				try {
					if (dataset_id >= 0) 
						H5Dwrite(dataset_id, H5T_NATIVE_UINT16, H5S_ALL, H5S_ALL,
								H5P_DEFAULT, flatArray);
				}
				catch (Exception e) {
					e.printStackTrace();
				}
				
				IJ.log("write uint16 hdf5");
				IJ.log("compressionLevel: " + String.valueOf(compressionLevel));
				IJ.log("Done");
			}
			else if (imgColorType == ImagePlus.GRAY32)
			{
				float[] pixels = null;
				stack = image.getStack();


				MDFloatArray arr = new MDFloatArray( channelDims);
				float[] flatArray = arr.getAsFlatArray();
				float[] flatArray2 = new float[flatArray.length];

				for (int i=1;i<=stack.getSize();i++){
					pixels = (float[]) stack.getPixels(i);

					System.arraycopy(pixels, 0,
							flatArray, (i-1)*(nRows*nCols), slizeSize);
				}
				System.arraycopy(flatArray, 0, flatArray2, 0, flatArray.length);
				for (int t=0; t<nFrames; t++) {
					for (int z=0; z<nLevs; z++) {
						for (int y=0; y<nRows; y++) {
							for (int x=0; x<nCols; x++) {
								int scrIndex = x + y*nCols + z*nCols*nRows + t*nCols*nRows*nLevs;
								int destIndex =  z + y*nLevs + x*nLevs*nRows + t*nLevs*nCols*nRows;
								flatArray[destIndex] = flatArray2[scrIndex];
							}
						}
					}
				}
				
				try {
					if ((file_id >= 0) && (dataspace_id >= 0))
						dataset_id =  H5Dcreate(file_id, "exported_data", H5T_NATIVE_FLOAT, dataspace_id,
								H5P_DEFAULT, dcpl_id, H5P_DEFAULT);
				}
				catch (Exception e) {
					e.printStackTrace();
				}

				try {
					if (dataset_id >= 0) 
						H5Dwrite(dataset_id, H5T_NATIVE_FLOAT, H5S_ALL, H5S_ALL,
								H5P_DEFAULT, flatArray);
				}
				catch (Exception e) {
					e.printStackTrace();
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
					dataspace_id_color = H5Screate_simple(5, channelDimsRGB, null);
				}
				catch (Exception e) {
					e.printStackTrace();
				}

				stack = image.getStack();

				MDByteArray arr = new MDByteArray(channelDimsRGB);		    
				byte[] flatArray = arr.getAsFlatArray();
				byte[] flatArray2 = new byte[flatArray.length];


				System.arraycopy(flatArray, 0, flatArray2, 0, flatArray.length);
				for (int t=0; t<nFrames; t++) {
					for (int z=0; z<nLevs; z++) {
						for (int c=0; c<3; c++){

							int stackIndex = image.getStackIndex(1, z + 1, t + 1);
							ColorProcessor cp = (ColorProcessor)(stack.getProcessor(stackIndex));
							byte[] red   = cp.getChannel(1);
							byte[] green = cp.getChannel(2);
							byte[] blue  = cp.getChannel(3);


							for (int y=0; y<nRows; y++) {
								for (int x=0; x<nCols; x++) {
									int scrIndex = x + y*nCols;
									int destIndex = z*3 + y*nLevs*3 + x*nLevs*nRows*3 + t*nLevs*nCols*nRows*3;
									flatArray[destIndex+0] = red[scrIndex];
									flatArray[destIndex+1] = green[scrIndex];
									flatArray[destIndex+2] = blue[scrIndex];	
								}
							}
						}	
					}
				}
				
				try {
					if ((file_id >= 0) && (dataspace_id_color >= 0))
						dataset_id =  H5Dcreate(file_id, "exported_data", H5T_NATIVE_UINT8, dataspace_id_color,
								H5P_DEFAULT, dcpl_id, H5P_DEFAULT);
				}
				catch (Exception e) {
					e.printStackTrace();
				}
				try {
					if (dataset_id >= 0) 
						H5Dwrite(dataset_id, H5T_NATIVE_UINT8, H5S_ALL, H5S_ALL,
								H5P_DEFAULT, flatArray);
				}
				catch (Exception e) {
					e.printStackTrace();
				}
				
				IJ.log("write uint8 RGB HDF5");
				IJ.log("compressionLevel: " + String.valueOf(compressionLevel));
				IJ.log("Done");

			}
			else {
			      IJ.error("Type Not handled yet!");
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

//				ImagePlus image = IJ.openImage("/Users/jmassa/Documents/ilastik/datasets/3D_LargeWhirl.tif");
//				ImagePlus image = IJ.openImage("/Users/jmassa/Documents/MaMut_project/rapoport/raw.tif");
		//		ImagePlus image = IJ.openImage("/Users/jmassa/Documents/MaMut_project/drosophila/ilastik_export/Raw_Data_0_10.tif");
		//		ImagePlus image = IJ.openImage("/Users/chaubold/hci/data/virginie/Number3/MI_Substack (1-170).tif");
		//		ImagePlus image = IJ.openImage("/Users/jmassa/Documents/workspace/plugin_test/droso.h5");
		//		image.show();

		// run the plugin
		//IJ.runPlugIn(clazz.getName(), "");
	}
}