import de.embl.cba.spindle3d.Spindle3DCommand;
import net.imagej.ImageJ;
import net.imglib2.type.numeric.RealType;

import java.io.File;

public class RunSpindleMorphometry
{
	public static < R extends RealType< R > > void main( String[] args )
	{
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();

		final Spindle3DCommand< R > command = new Spindle3DCommand<>();
		command.opService = ij.op();
		command.scriptService = ij.script();

		// Spindle touching objects
		command.inputImageFile = new File("//Users/tischer/Documents/spindle3d/src/test/resources/multiple-dna/d1-t2-multiple-dna-4.tif" );

		command.outputDirectory = new File( "/Users/tischer/Desktop/kletter" );
		command.spindleChannelIndexOneBased = 2;
		command.dnaChannelIndexOneBased = 1;
		command.showIntermediateImages = true;
		command.showIntermediatePlots = false;
		command.saveResults = false;
		command.run();
	}
}
