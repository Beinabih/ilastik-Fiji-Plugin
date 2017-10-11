import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.io.SaveDialog;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import org.ilastik.IlastikHDF5Exporter;

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

		try {
			IlastikHDF5Exporter exporter = new IlastikHDF5Exporter(filename);
			exporter.export(image, compressionLevel, "exported_image");
			exporter.close();
		} catch (HDF5Exception err) {
			IJ.error("Error while saving '" + filename + "':\n"
					+ err);
		} catch (Exception err) {
			IJ.error("Error while saving '" + filename + "':\n"
					+ err);
		} catch (OutOfMemoryError o) {
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
