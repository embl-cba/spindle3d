package de.embl.cba.spindle3d.command;

import ij.IJ;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

@Plugin(type = Command.class, menuPath = "Plugins>Spindle3D>Spindle3D Download Example Image..." )
public class Spindle3DDownloadSampleImageCommand implements Command
{
	public static final String HELA = "Mouse embryonic stem cell expressing GFP-tagged tubulin -- Ch1 Tubulin Ch2 DNA";

	@Parameter ( label = "Image", choices = { HELA } )
	String imageName;

	Map< String, String > imageNameToURL = new HashMap<>();

	public void run()
	{
		initImageMap();

		IJ.openImage( imageNameToURL.get( imageName ) ).show();
	}

	public void initImageMap()
	{
		imageNameToURL.put( HELA, "https://github.com/tischi/spindle3d/raw/master/src/test/resources/publication/20201209_R1E309_TubGFP_DM1a_KATNA1_D0_011-3.tif" );
	}
}
