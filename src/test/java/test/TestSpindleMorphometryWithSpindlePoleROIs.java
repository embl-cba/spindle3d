package test;

import de.embl.cba.spindle3d.command.Spindle3DProcessFileCommand;
import net.imagej.ImageJ;
import org.junit.Test;

import java.io.File;

public class TestSpindleMorphometryWithSpindlePoleROIs
{
	public static void main( String[] args )
	{
		new TestSpindleMorphometryWithSpindlePoleROIs().test();
	}

	@Test
	public void test()
	{
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();

		final Spindle3DProcessFileCommand< ? > command = new Spindle3DProcessFileCommand<>();
		command.opService = ij.op();
		command.scriptService = ij.script();

		// Spindle touching objects
		command.inputImageFile = new File("src/test/resources/test/with-spindle-pole-rois/pointROIs.tif" );

		command.outputDirectory = new File( "src/test/resources/test/output" );
		command.spindleChannelIndexOneBased = 1;
		command.dnaChannelIndexOneBased = 2;
		command.showIntermediateImages = false;
		command.showIntermediatePlots = false;
		command.saveResults = true;
		command.run();
	}
}
