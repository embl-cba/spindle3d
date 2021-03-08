package de.embl.cba.spindle3d.command;

import de.embl.cba.spindle3d.Spindle3DCommand;
import ij.ImagePlus;
import net.imglib2.type.numeric.RealType;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin(type = Command.class, menuPath = "Plugins>Spindle3D>Spindle3D Process Current Image..." )
public class Spindle3DProcessCurrentImageCommand< R extends RealType< R > > extends Spindle3DCommand
{
	@Parameter ( label = "Input Image" )
	public ImagePlus inputImagePlus;

	public void run()
	{
		if ( ! fetchSettingAndInit() ) return;
		processImagePlus( inputImagePlus, null );
	}
}
