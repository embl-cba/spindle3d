package test;

import de.embl.cba.spindle3d.Spindle3DMeasurements;
import de.embl.cba.spindle3d.command.Spindle3DFileProcessorCommand;
import loci.common.DebugTools;
import net.imagej.ImageJ;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.Map;

public class TestWithCellMask
{
	public static void main( String[] args )
	{
		new TestWithCellMask().test();
	}

	@Test
	public void test()
	{
		DebugTools.setRootLevel("OFF");

		final ImageJ ij = new ImageJ();
		ij.ui().showUI();

		final Spindle3DFileProcessorCommand command = new Spindle3DFileProcessorCommand();
		command.opService = ij.op();
		command.scriptService = ij.script();

		// Spindle touching objects
		command.inputImageFile = new File("src/test/resources/test/with-cell-mask/20190227_HighZoom--W0000--P0001-T0004--0001.tif" );

		command.outputDirectory = new File( "src/test/resources/test/output" );
		command.spindleChannelIndexOneBased = 1;
		command.dnaChannelIndexOneBased = 2;
		command.showIntermediateImages = false;
		command.showIntermediatePlots = false;
		command.saveResults = true;
		command.run();

		final Map< String, Object > measured = command.getObjectMeasurements().get( 0 );
		final Double surface = (Double) measured.get( Spindle3DMeasurements.getCellSurfaceMeasurementName() );
		final Double volume = (Double) measured.get( Spindle3DMeasurements.getCellVolumeMeasurementName() );

		Assert.assertEquals( surface, 1000, 150 );
		Assert.assertEquals( volume, 2488, 50 );
	}
}
