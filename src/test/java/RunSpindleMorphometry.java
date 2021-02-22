import de.embl.cba.spindle3d.Spindle3DCommand;
import de.embl.cba.spindle3d.Spindle3DFileCommand;
import net.imagej.ImageJ;
import net.imglib2.type.numeric.RealType;

import java.io.File;

public class RunSpindleMorphometry
{
	public static < R extends RealType< R > > void main( String[] args )
	{
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();

		final Spindle3DFileCommand< R > command = new Spindle3DFileCommand<>();
		command.opService = ij.op();
		command.scriptService = ij.script();

		// Spindle touching objects
		command.inputImageFile = new File("//Users/tischer/Documents/spindle3d/src/test/resources/multiple-dna/d1-t2-multiple-dna-0.tif" );

		command.outputDirectory = new File( "/Users/tischer/Desktop/kletter" );
		command.spindleChannelIndexOneBased = 2;
		command.dnaChannelIndexOneBased = 1;
		command.showIntermediateImages = false;
		command.showIntermediatePlots = true;
		command.saveResults = false;
		command.run();
	}
}
