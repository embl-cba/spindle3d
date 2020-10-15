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

		// Spindle touching objects
		command.inputImageFile = new File("/Users/tischer/Desktop/kletter/spindle-test-data/20201006_R1E309_TubGFP_KATNA1_D0_016-3.tif" );

		command.outputDirectory = new File( "/Users/tischer/Desktop/kletter" );
		command.spindleChannelIndexOneBased = 2;  // normally 1
		command.dnaChannelIndexOneBased = 1; // normally 2
		command.showIntermediateResults = false;
		command.saveResults = true;
		command.run();
	}
}
