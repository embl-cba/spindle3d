package test.groundtruth;

import de.embl.cba.spindle3d.Spindle3DMeasurements;
import de.embl.cba.spindle3d.command.Spindle3DProcessFileCommand;
import loci.common.DebugTools;
import net.imagej.ImageJ;
import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;

public class TestGT1
{
	public static void main( String[] args )
	{
		new TestGT1().run();
	}

	@Test
	public void run()
	{
		DebugTools.setRootLevel("OFF");

		final ImageJ ij = new ImageJ();

		final Spindle3DProcessFileCommand< ? > command = new Spindle3DProcessFileCommand<>();
		command.opService = ij.op();
		command.scriptService = ij.script();
		command.inputImageFile = new File("src/test/resources/test/groundtruth/gt_01.zip" );
		command.outputDirectory = new File( "src/test/resources/test/output" );
		command.dnaChannelIndexOneBased = 1;
		command.spindleChannelIndexOneBased = 2;
		command.showIntermediateImages = false;
		command.showIntermediatePlots = false;
		command.saveResults = false;
		command.run();

		final HashMap< Integer, Map< String, Object > > measurements = command.getObjectMeasurements();

		final Map< String, Object > features = measurements.get( 0 );

		assertEquals( 10.5, (double) features.get( Spindle3DMeasurements.SPINDLE_LENGTH ), 1.0 );
		assertEquals( 7.4, (double) features.get( Spindle3DMeasurements.SPINDLE_WIDTH_AVG ), 1.0 );
		assertEquals( 5.0, (double) features.get( Spindle3DMeasurements.SPINDLE_ANGLE_DEGREES ), 10.0 );

	}
}
