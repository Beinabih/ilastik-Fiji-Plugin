
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JTable;

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

/**
 * ilastik export
 *
 * A template for import HDf5 of either
 * GRAY8, GRAY16, GRAY32 or COLOR_RGB images.
 *
 * @author j.M
 */
public class ilastik_import implements PlugIn {
	
	protected ImagePlus image;
	protected ImageStack stack;

	// image property members
	private int width;
	private int height;
	private JTable pathTable_;
	private String fullFileName_;
	

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
	    
	    IHDF5Reader reader = HDF5Factory.openForReading(fullFileName_);
	    
//	    path inside HDF5
	    HDF5LinkInformation link = reader.object().getLinkInformation("/");
	    
	    List<HDF5LinkInformation> members = reader.object().getGroupMemberInformation(link.getPath(), true);
	    
	    for (HDF5LinkInformation info : members)
	    {
	      IJ.log(info.getPath() + ":" + info.getType());
	      switch (info.getType())
	      {
	      case DATASET:
	    	  HDF5DataSetInformation dsInfo = reader.object().getDataSetInformation(info.getPath());
	    	  String typeText = getInfo(dsInfo);
	    	  IJ.log(" Type " + typeText);
	      case SOFT_LINK:
	    	  break;
	      case GROUP:
	    	  break;
	      default:
	    	  break;
	      }
	    }
//	    IHDF5ReaderConfigurator conf = HDF5Factory.configureForReading(fullFileName_);
//	    IHDF5Reader reader = conf.reader();
//	    
	  
	    
//
//        if (typeText.equals( "uint8") && isRGB == false) {
//        	
//        	
//        	
//        }
	    

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