package org.ilastik;

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
import ch.systemsx.cisd.base.mdarray.MDIntArray;
import ch.systemsx.cisd.base.mdarray.MDLongArray;
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
import ch.systemsx.cisd.hdf5.UnsignedIntUtils;
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
 * ilastik Import
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
	private JComboBox dimBox;
	private JFrame errorWindow;
	private boolean isList;
	private JFrame frame;
	private JFrame frame2;
	private JPanel panel;
	private JPanel panel2;
	private String dimensionOrder;

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
		this.name = name;
		


		try
		{
			this.reader = HDF5Factory.openForReading(fullFileName_);
			String path = "/";
			findData(reader, path);
			if (datasetList.size() == 1)
			{
//				lookupWindow();
				dimensionWindow();
				this.isList = false;
			}
			else
			{
				chooseData(reader, datasetList);
				this.isList = true;
			}


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
		setLocationRelativeTo(null);
		pack();
		setVisible(true);

	}
	
	public void lookupWindow(){
		
		frame = new JFrame();
		JButton l1 = new JButton("Load Raw");
		l1.setActionCommand("Load Raw");
		l1.addActionListener(this);
		JButton l2 = new JButton("Load LUT");
		l2.setActionCommand("Load LUT");
		l2.addActionListener(this);
		
//		panel = new JPanel();
//		
//		panel.add(l1);
//		panel.add(l2);
//		
		frame.getContentPane().add(l1, BorderLayout.LINE_START );
		frame.getContentPane().add(l2, BorderLayout.LINE_END);
		frame.setResizable(false);
		frame.setLocationRelativeTo(null);
		frame.pack();
		frame.setVisible(true);
		
		
	}
	
	public void dimensionWindow(){
		
		String path;
		String boxInfo;
		int rank = 0;
		String[] dimExamples = new String[20];;

		
		frame2 = new JFrame();
		
		JButton k1 = new JButton("Load");
		k1.setActionCommand("load2");
		k1.addActionListener(this);
		JButton k2 = new JButton("Cancel");
		k2.setActionCommand("cancel2");
		k2.addActionListener(this);
		
		if (this.isList){
			boxInfo = (String)dataSetBox.getSelectedItem();
			String[] parts = boxInfo.split(":");
			path = parts[1].replaceAll("\\s+","");
//			IJ.log(boxInfo);
		}
		else{
			path = datasetList.get(0);
//			IJ.log(path);
		}
		HDF5DataSetInformation dsInfo = reader.object().getDataSetInformation(path);
		rank = dsInfo.getRank();
		
		if (rank == 5){
			dimExamples[0] = "txyzc";}
		else if (rank == 4){
			dimExamples[0] = "xyzc";
			dimExamples[1] = "txyz";
			dimExamples[2] = "txyc";
		}
		else if (rank == 3){
			dimExamples[0] = "xyc";
			dimExamples[1] = "xyz";
			
		}
		else{
			dimExamples[0] = "xy";}
			
		this.dimBox = new JComboBox(dimExamples);
		dimBox.setEditable(true);
		dimBox.addActionListener(this);
		
		frame2.getContentPane().add(dimBox, BorderLayout.PAGE_START);
		frame2.getContentPane().add(k1, BorderLayout.LINE_START );
		frame2.getContentPane().add(k2, BorderLayout.LINE_END);
		frame2.setResizable(false);
		frame2.setLocationRelativeTo(null);
		frame2.pack();
		frame2.setVisible(true);
		
		IJ.log("This version supports images with fewer dimensions which have the order txyzc");
		
		
	}

	public void getData( List<String> datasetList, String dimensionOrder){
		int rank      = 0;
		int nLevels   = 0;
		int nRows     = 0;
		int nCols     = 0;
		int nChannels = 0;
		int nFrames = 0 ;
		double maxGray = 1;
		String path;
		String boxInfo;

		boolean isRGB = false;
		ImagePlus imp = null;

		if (this.isList){
			boxInfo = (String)dataSetBox.getSelectedItem();
			String[] parts = boxInfo.split(":");
			path = parts[1].replaceAll("\\s+","");
//			IJ.log(boxInfo);
		}
		else{
			path = datasetList.get(0);
//			IJ.log(path);
		}

		HDF5DataSetInformation dsInfo = reader.object().getDataSetInformation(path);

		if (imp == null) {
			//get rank
//			rank = dsInfo.getRank();

			//get type information
			String typeText = getInfo(dsInfo);
			
				nCols = 1;
				nRows = 1;
				nLevels = 1;
				nChannels = 1;
				nFrames = 1;
				
				for(int index =0; index < dimensionOrder.length(); index++){
					
					char axis = dimensionOrder.charAt(index);
					
					if (axis == 't'){
						nFrames = (int)dsInfo.getDimensions()[index];
					}
					if (axis == 'x'){
						nCols = (int)dsInfo.getDimensions()[index];
					}
					if (axis == 'y'){
						nRows = (int)dsInfo.getDimensions()[index];
					}
					if (axis == 'z'){
						nLevels = (int)dsInfo.getDimensions()[index];
					}
					if (axis == 'c'){
						nChannels = (int)dsInfo.getDimensions()[index];
					}
					
					if (nChannels == 3) {
					isRGB = true;
				}
					
				}
				
//			if (rank == 5) {
//				nFrames = (int)dsInfo.getDimensions()[0];
//				nCols = (int)dsInfo.getDimensions()[1];
//				nRows = (int)dsInfo.getDimensions()[2];
//				nLevels = (int)dsInfo.getDimensions()[3];
//				nChannels = (int)dsInfo.getDimensions()[4];   
//				IJ.log("Dimensions: " + String.valueOf(nFrames) + "x" + String.valueOf(nCols) + "x" 
//						+ String.valueOf(nRows) + "x" + String.valueOf(nLevels) + "x" + String.valueOf(nChannels));
//				if (nChannels == 3) {
//					isRGB = true;
//				}
//			} else {
//
//				IJ.error(" the data should have 5 dimensions");
//				IJ.log("Dimension Error: the data has " + String.valueOf(rank) + " dimensions" );
//				IJ.log("This plugin only works for 5 dimensional datasets");
//				IJ.log("Please use the HDF5 plugin:");
//				IJ.log("http://lmb.informatik.uni-freiburg.de/resources/opensource/imagej_plugins/hdf5.html");
//				return;
//			}
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
//									int scrIndex = c + x*nChannels + y*nCols*nChannels+ lev*nCols*nRows*nChannels + frame*nLevels*nCols*nRows*nChannels ;
									int destIndex = y*nCols + x;
									destData[destIndex] = flat_data[scrIndex];
								}
							}
						}
					}
				}
				maxGray = 255;
				IJ.log("DONE");
			}
				
				else if (typeText.equals( "uint8") && isRGB == true) {

					IJ.log("Bit-depth: " + String.valueOf(typeText));
					IJ.log("Loading Data");
					MDByteArray rawdata = reader.uint8().readMDArray(path);
					byte[] flat_data = rawdata.getAsFlatArray();


					imp = IJ.createHyperStack( name , 
							nCols, nRows, nChannels, nLevels, nFrames, 24);

					for (int frame = 0; frame < nFrames; ++frame) {
						for( int lev = 0; lev < nLevels; ++lev) {
							for (int c = 0; c < nChannels; ++c) {

								ImageProcessor ip = imp.getStack().getProcessor(imp.getStackIndex(
										c+1, lev+1, frame+1));
								int[] destData = (int[])ip.getPixels();

								for (int x=0; x<nCols; x++) {
									for (int y=0; y<nRows; y++) {
										int scrIndex = lev*nChannels + y*nLevels*nChannels+ x*nLevels*nRows*nChannels + frame*nLevels*nCols*nRows*nChannels ;
										int destIndex = y*nCols + x;
										int red   = flat_data[scrIndex] & 0xff;
										int green = flat_data[scrIndex + 1] & 0xff;
										int blue  = flat_data[scrIndex +2 ] & 0xff;
										destData[destIndex] = (red<<16) + (green<<8) + blue;
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
				
			} else if (typeText.equals("uint32")){
				//uint32 will be converted to float32

				IJ.log("Bit-depth: " + String.valueOf(typeText));
				IJ.log("Loading Data");
				MDIntArray rawdata = reader.uint32().readMDArray(path);
				int[] flat_data = rawdata.getAsFlatArray();

				imp = IJ.createHyperStack( name , 
						nCols, nRows, nChannels, nLevels, nFrames, 32);

				for (int frame = 0; frame < nFrames; ++frame) {
					for( int lev = 0; lev < nLevels; ++lev) {
						for (int c = 0; c < nChannels; ++c) {

							ImageProcessor ip = imp.getStack().getProcessor( imp.getStackIndex(
									c +1, lev+1, frame+1));
							float[] destData = (float[]) ip.getPixels();

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
		IJ.log(name);
		imp.setTitle(name);
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
//			lookupWindow();
			dimensionWindow(); 
		}
		else if (event.getActionCommand().equals("cancel")) 
		{
			dispose();
		}
		else if (event.getActionCommand().equals("Load Raw"))
		{
			frame.dispose();
//			dimensionWindow();
			getData(datasetList, dimensionOrder);
		}
		else if (event.getActionCommand().equals("Load LUT"))
		{
			frame.dispose();
//			dimensionWindow(); 
			getData(datasetList, dimensionOrder);
			IJ.run("3-3-2 RGB");
		}
		
		if (event.getActionCommand().equals("load2")) 
		{
			dimensionOrder = (String) dimBox.getSelectedItem();
			lookupWindow();
//			getData(datasetList, dimensionOrder);
			frame2.dispose();
		}
		else if (event.getActionCommand().equals("cancel2")) 
		{
			frame2.dispose();
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

		new ImageJ();
	}

}