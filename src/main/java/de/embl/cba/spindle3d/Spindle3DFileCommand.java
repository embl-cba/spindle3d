package de.embl.cba.spindle3d;

import net.imglib2.type.numeric.RealType;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;

@Plugin(type = Command.class, menuPath = "Plugins>Spindle3D>Spindle3D Image from File..." )
public class Spindle3DFileCommand< R extends RealType< R > > extends Spindle3DCommand
{
	@Parameter ( label = "Input Image File" )
	public File inputImageFile;

	public void run()
	{
		if ( ! fetchSettingAndInit() ) return;
		processFile( inputImageFile.toString() );
	}
}
