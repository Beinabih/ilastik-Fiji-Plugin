import java.awt.BorderLayout;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ch.systemsx.cisd.base.mdarray.MDAbstractArray;
import ch.systemsx.cisd.base.mdarray.MDByteArray;
import ch.systemsx.cisd.base.mdarray.MDFloatArray;
import ch.systemsx.cisd.base.mdarray.MDShortArray;
import ch.systemsx.cisd.hdf5.HDF5DataSetInformation;
import ch.systemsx.cisd.hdf5.HDF5DataTypeInformation;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.HDF5FloatStorageFeatures;
import ch.systemsx.cisd.hdf5.HDF5IntStorageFeatures;
import ch.systemsx.cisd.hdf5.HDF5LinkInformation;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import ch.systemsx.cisd.hdf5.IHDF5ReaderConfigurator;
import ch.systemsx.cisd.hdf5.IHDF5Writer;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.io.OpenDialog;
import ij.io.SaveDialog;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import ij.plugin.*;
import java.util.Vector;
import javax.swing.*;

/**
 * ilastik export
 *
 * A template for import HDf5 of either
 * GRAY8, GRAY16, GRAY32 or COLOR_RGB images.
 *
 * @author j.M
 */
public class ilastik_import extends JFrame implements PlugIn, ActionListener {

	protected ImagePlus image;
	protected ImageStack stack;

	// image property members
	private int width;
	private int height;
	private String fullFileName_;
	private  List<String> datasetList;
	private IHDF5Reader reader;
	private JComboBox dataSetBox;
	private JFrame errorWindow;


	// plugin parameters
	public double value;
	public String name;


	@Override
	public void run(String arg) {
		String directory = "";
		String name = "";
		boolean tryAgain;
		String openMSG = "Open HDF5...";
		do {
			tryAgain = false;
			OpenDialog od;
			if (directory.equals(""))
				od = new OpenDialog(openMSG, "");
			else
				od = new OpenDialog(openMSG, directory, "");

			directory = od.getDirectory();
			name = od.getFileName();
			if (name == null)
				return;
			if (name == "")
				return;

			File testFile = new File(directory + name);
			if (!testFile.exists() || !testFile.canRead())
				return;

			if (testFile.isDirectory()) {
				directory = directory + name;
				tryAgain = true;
			}
		} while (tryAgain);

		fullFileName_ = directory + name;
		IJ.showStatus("Loading HDF5 File: " + fullFileName_);

		this.datasetList = new ArrayList<String>();

		try
		{
			this.reader = HDF5Factory.openForReading(fullFileName_);
			String path = "/";
			findData(reader, path);
			chooseData(reader, datasetList);


		}
		catch (HDF5Exception err) 
		{
			IJ.error("Error while opening '" + fullFileName_ 
					+ err);
		} 
		catch (Exception err) 
		{
			IJ.error("Error while opening '" + fullFileName_
					+ err);
		} 
		catch (OutOfMemoryError o) 
		{
			IJ.outOfMemory("Load HDF5");
		}

	}


	static String getInfo(HDF5DataSetInformation dsInfo){

		HDF5DataTypeInformation dsType = dsInfo.getTypeInformation();
		String type = "";

		if (dsType.isSigned() == false) {
			type += "u";
		}

		switch( dsType.getDataClass())
		{
		case INTEGER:
			type += "int" + 8*dsType.getElementSize();
			break;
		case FLOAT:
			type += "float" + 8*dsType.getElementSize();
			break;
		default:
			type += dsInfo.toString();
		}
		return type;
	}

	public void findData(IHDF5Reader reader, String path){

		//	    path inside HDF5
		HDF5LinkInformation link = reader.object().getLinkInformation(path);

		List<HDF5LinkInformation> members = reader.object().getGroupMemberInformation(link.getPath(), true);

		for (HDF5LinkInformation info : members)
		{
			IJ.log(info.getPath() + ": " + info.getType());
			switch (info.getType())
			{
			case DATASET:
				datasetList.add(info.getPath());		

			case SOFT_LINK:
				break;
			case GROUP:
				path = info.getPath();
				findData(reader, path);

			default:
				break;
			}
		}

	}

	public void chooseData(IHDF5Reader reader, List<String> datasetList ){


		JButton b1 = new JButton("Load");
		b1.setActionCommand("load");
		b1.addActionListener(this);
		JButton b2 = new JButton("Cancel");
		b2.setActionCommand("cancel");
		b2.addActionListener(this);

		String[] dataSets = new String[datasetList.size()];
		dataSets = datasetList.toArray(dataSets);
		
		this.dataSetBox = new JComboBox(new ilastikBoxModel());
		
		for(int i =0; i < datasetList.size(); i++){
			
			if (reader.object().getDataSetInformation(dataSets[i]).getRank() == 5){
				
				dataSetBox.addItem(new comboBoxDimensions(dataSets[i], "+"));
			}
			else{
				dataSetBox.addItem(new comboBoxDimensions(dataSets[i], "-"));
			}
			
		}

		//this.dataSetBox = new JComboBox(dataSets);
		//	    dataSetBox.setSelectedIndex(0);
		dataSetBox.addActionListener(this);

		getContentPane().add(dataSetBox, BorderLayout.PAGE_START);
		getContentPane().add(b1, BorderLayout.LINE_START );
		getContentPane().add(b2, BorderLayout.LINE_END);
		setResizable(false);
		pack();
		setVisible(true);

	}

	public void getData(){
		int rank      = 0;
		int nLevels   = 0;
		int nRows     = 0;
		int nCols     = 0;
		int nChannels = 0;
		int nFrames = 0 ;
		double maxGray = 1;
		String path;

		boolean isRGB = false;
		ImagePlus imp = null;


		String boxInfo = (String)dataSetBox.getSelectedItem();
		String[] parts = boxInfo.split(":");
		path = parts[1].replaceAll("\\s+","");
		
		IJ.log(path);

		HDF5DataSetInformation dsInfo = reader.object().getDataSetInformation(path);


		if (imp == null) {
			//	        	  get rank
			rank = dsInfo.getRank();

			//	              get type information
			String typeText = getInfo(dsInfo);

			if (rank == 5) {
				nFrames = (int)dsInfo.getDimensions()[0];
				nCols = (int)dsInfo.getDimensions()[1];
				nRows = (int)dsInfo.getDimensions()[2];
				nLevels = (int)dsInfo.getDimensions()[3];
				nChannels = (int)dsInfo.getDimensions()[4];   
				IJ.log("Dimensions: " + String.valueOf(nFrames) + "x" + String.valueOf(nCols) + "x" 
						+ String.valueOf(nRows) + "x" + String.valueOf(nLevels) + "x" + String.valueOf(nChannels));
			} else {
			
				IJ.error(" the data should have 5 dimensions");
				IJ.log("Dimension Error: the data has " + String.valueOf(rank) + " dimensions" );
				IJ.log("This plugin only works for 5 dimensional datasets");
				IJ.log("Please use the HDF5 plugin:");
				IJ.log("http://lmb.informatik.uni-freiburg.de/resources/opensource/imagej_plugins/hdf5.html");
				return;
			}
			int sliceSize = nCols * nRows;

			if (typeText.equals( "uint8") && isRGB == false) {

				IJ.log("Bit-depth: " + String.valueOf(typeText));
				IJ.log("Loading Data");
				MDByteArray rawdata = reader.uint8().readMDArray(path);
				byte[] flat_data = rawdata.getAsFlatArray();


				imp = IJ.createHyperStack( name , 
						nCols, nRows, nChannels, nLevels, nFrames, 8);

				for (int frame = 0; frame < nFrames; ++frame) {
					for( int lev = 0; lev < nLevels; ++lev) {
						for (int c = 0; c < nChannels; ++c) {

							ImageProcessor ip = imp.getStack().getProcessor(imp.getStackIndex(
									c+1, lev+1, frame+1));
							byte[] destData = (byte[])ip.getPixels();

							for (int x=0; x<nCols; x++) {
								for (int y=0; y<nRows; y++) {
									int scrIndex = c + lev*nChannels + y*nLevels*nChannels+ x*nLevels*nRows*nChannels + frame*nLevels*nCols*nRows*nChannels ;
									int destIndex = y*nCols + x;
									destData[destIndex] = flat_data[scrIndex];
								}
							}
						}
					}
				}
				maxGray = 255;
				IJ.log("DONE");

			} else if (typeText.equals("uint16")){

				IJ.log("Bit-depth: " + String.valueOf(typeText));
				IJ.log("Loading Data");
				MDShortArray rawdata = reader.uint16().readMDArray(path);
				short[] flat_data = rawdata.getAsFlatArray();

				imp = IJ.createHyperStack( name , 
						nCols, nRows, nChannels, nLevels, nFrames, 16);

				for (int frame = 0; frame < nFrames; ++frame) {
					for( int lev = 0; lev < nLevels; ++lev) {
						for (int c = 0; c < nChannels; ++c) {

							ImageProcessor ip = imp.getStack().getProcessor(imp.getStackIndex(
									c +1, lev+1, frame+1));
							short[] destData = (short[])ip.getPixels();

							for (int x=0; x<nCols; x++) {
								for (int y=0; y<nRows; y++) {
									int scrIndex = c + lev*nChannels + y*nLevels*nChannels+ x*nLevels*nRows*nChannels + frame*nLevels*nCols*nRows*nChannels ;
									int destIndex = y*nCols + x;
									destData[destIndex] = flat_data[scrIndex];
								}
							}
						}
					}
				}

				for (int i = 0; i < flat_data.length; ++i) {
					if (flat_data[i] > maxGray) maxGray = flat_data[i];
				}
				IJ.log("DONE");

			} else if (typeText.equals("int16")){

				IJ.log("Bit-depth: " + String.valueOf(typeText));
				IJ.log("Loading Data");
				MDShortArray rawdata = reader.int16().readMDArray(path);
				short[] flat_data = rawdata.getAsFlatArray();

				imp = IJ.createHyperStack( name , 
						nCols, nRows, nChannels, nLevels, nFrames, 16);

				for (int frame = 0; frame < nFrames; ++frame) {
					for( int lev = 0; lev < nLevels; ++lev) {
						for (int c = 0; c < nChannels; ++c) {

							ImageProcessor ip = imp.getStack().getProcessor( imp.getStackIndex(
									c +1, lev+1, frame+1));
							short[] destData = (short[])ip.getPixels();

							for (int x=0; x<nCols; x++) {
								for (int y=0; y<nRows; y++) {
									int scrIndex = c + lev*nChannels + y*nLevels*nChannels+ x*nLevels*nRows*nChannels + frame*nLevels*nCols*nRows*nChannels ;
									int destIndex = y*nCols + x;
									destData[destIndex] = flat_data[scrIndex];
								}
							}
						}
					}
				}
				for (int i = 0; i < flat_data.length; ++i) {
					if (flat_data[i] > maxGray) maxGray = flat_data[i];
				}
				IJ.log("DONE");

			} else if (typeText.equals("float32") || typeText.equals("float64") ) {

				IJ.log("Bit-depth: " + String.valueOf(typeText));
				IJ.log("Loading Data");
				MDFloatArray rawdata = reader.float32().readMDArray(path);
				float[] flat_data = rawdata.getAsFlatArray();

				imp = IJ.createHyperStack( name , 
						nCols, nRows, nChannels, nLevels, nFrames, 32);

				for (int frame = 0; frame < nFrames; ++frame) {
					for( int lev = 0; lev < nLevels; ++lev) {
						for (int c = 0; c < nChannels; ++c) {

							ImageProcessor ip = imp.getStack().getProcessor( imp.getStackIndex(
									c +1, lev+1, frame+1));
							float[] destData = (float[])ip.getPixels();

							for (int x=0; x<nCols; x++) {
								for (int y=0; y<nRows; y++) {
									int scrIndex = c + lev*nChannels + y*nLevels*nChannels+ x*nLevels*nRows*nChannels + frame*nLevels*nCols*nRows*nChannels ;
									int destIndex = y*nCols + x;
									destData[destIndex] = flat_data[scrIndex];
								}
							}
						}
					}
				}
				for (int i = 0; i < flat_data.length; ++i) {
					if (flat_data[i] > maxGray) maxGray = flat_data[i];
				}
				IJ.log("DONE");

			}
		}
		reader.close();
		dispose();

		for( int c = 1; c <= nChannels; ++c)
		{
			imp.setC(c);
			imp.setDisplayRange(0,maxGray);
		}

		imp.show();

	}



	public void showAbout() {
		IJ.showMessage("ilastik Export",
				"a template for Import data into hdf5 for ilastik"
				);
	}

	public void actionPerformed(ActionEvent event) 
	{
		if (event.getActionCommand().equals("load")) 
		{
			getData();
		}
		else if (event.getActionCommand().equals("cancel")) 
		{
			dispose();
		}
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
		Class<?> clazz = ilastik_import.class;
		String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
		String pluginsDir = url.substring("file:".length(), url.length() - clazz.getName().length() - ".class".length());
		System.setProperty("plugins.dir", pluginsDir);

		// start ImageJ
		new ImageJ();

		// open the Clown sample
		//		ImagePlus image = IJ.openImage("/Users/jmassa/Documents/MaMut_project/drosophila/ilastik_export/Raw_Data_0_10.tif");
		//		ImagePlus image = IJ.openImage("/Users/jmassa/Documents/MaMut_project/rapoport/raw.tif");
		//		image.show();

		// run the plugin
		//IJ.runPlugIn(clazz.getName(), "");
	}
}