package develop;

import de.embl.cba.spindle3d.command.Spindle3DFileProcessorCommand;
import de.embl.cba.spindle3d.command.advanced.Spindle3DAdvancedFileProcessorCommand;
import loci.common.DebugTools;
import net.imagej.ImageJ;

import java.io.File;

public class DevelopManualDnaThreshold
{
	public static void main( String[] args )
	{
		final ImageJ ij = new ImageJ();
		DebugTools.setRootLevel("OFF");

		final Spindle3DAdvancedFileProcessorCommand command = new Spindle3DAdvancedFileProcessorCommand();
		command.opService = ij.op();
		command.scriptService = ij.script();
		command.outputDirectory = new File( "/Users/tischer/Downloads" );
		command.dnaChannelIndexOneBased = 2;
		command.spindleChannelIndexOneBased = 1;
		command.showIntermediateImages = true;
		command.showIntermediatePlots = false;
		command.saveResults = true;
		command.manualDnaThreshold = 2000;
		command.inputImageFile = new File( "/Users/tischer/Downloads/TestStack_1.tif" );
		command.run();
	}
}
