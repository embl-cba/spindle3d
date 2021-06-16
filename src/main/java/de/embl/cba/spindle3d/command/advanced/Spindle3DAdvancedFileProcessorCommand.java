package de.embl.cba.spindle3d.command.advanced;

import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;

/**
 * This is only for development purposes.
 * The parameters should not be changed by "normal" users.
 */
//@Plugin(type = Command.class, menuPath = "Plugins>Spindle3D>Spindle3D Process File (Advanced)..." )
public class Spindle3DAdvancedFileProcessorCommand extends Spindle3DAdvancedProcessor implements Command
{
	@Parameter ( label = "Input Image File" )
	public File inputImageFile;

	@Override
	public void run()
	{
		setAdvancedSettings();
		processFile( inputImageFile );
	}
}
