import de.embl.cba.spindle3d.Spindle3DAdvancedCommand;
import de.embl.cba.spindle3d.Spindle3DCommand;
import net.imagej.ImageJ;
import net.imglib2.type.numeric.RealType;

import java.io.File;

public class RunSpindle3DAdvancedCommand
{
	public static void main( String[] args )
	{
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();

		ij.command().run( Spindle3DAdvancedCommand.class, true );
	}
}
