import de.embl.cba.spindle3d.command.Spindle3DAdvancedCommand;
import loci.common.DebugTools;
import net.imagej.ImageJ;

public class RunSpindle3DAdvancedCommand
{
	public static void main( String[] args )
	{
		DebugTools.setRootLevel("OFF");

		final ImageJ ij = new ImageJ();
		ij.ui().showUI();

		ij.command().run( Spindle3DAdvancedCommand.class, true );
	}
}
