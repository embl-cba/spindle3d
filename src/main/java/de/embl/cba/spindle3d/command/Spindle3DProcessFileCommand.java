package de.embl.cba.spindle3d.command;

import net.imglib2.type.numeric.RealType;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;

@Plugin(type = Command.class, menuPath = "Plugins>Spindle3D>Spindle3D Process File..." )
public class Spindle3DProcessFileCommand< R extends RealType< R > > extends Spindle3DCommand< R >
{
	@Parameter ( label = "Input Image File" )
	public File inputImageFile;

	public void run()
	{
		if ( ! fetchSettingAndInit() ) return;
		processFile( inputImageFile.toString() );
	}
}
