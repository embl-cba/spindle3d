package de.embl.cba.spindle3d;

import ij.ImagePlus;
import ij.WindowManager;
import org.scijava.script.ScriptService;

import javax.script.ScriptException;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.concurrent.ExecutionException;

public class ScriptRunner implements Runnable
{
	private final ImagePlus imp;
	private final File file;
	private final ScriptService scriptService;
	private ImagePlus outputImp;

	public ScriptRunner( ImagePlus imp, File file, ScriptService scriptService )
	{
		this.imp = imp;
		this.file = file;
		this.scriptService = scriptService;
	}

	@Override
	public void run()
	{
		// TODO: make headless
		imp.show();

		tryRun();

		outputImp = WindowManager.getCurrentImage();
		outputImp.setTitle( "DNA Mask" );

		//imp.hide();
		//outputImp.hide();
	}

	private void tryRun()
	{
		try
		{
			scriptService.run( file, true ).get();
		} catch ( InterruptedException e )
		{
			e.printStackTrace();
		} catch ( ExecutionException e )
		{
			e.printStackTrace();
		} catch ( FileNotFoundException e )
		{
			e.printStackTrace();
		} catch ( ScriptException e )
		{
			e.printStackTrace();
		}
	}

	public ImagePlus getOutputImp()
	{
		return outputImp;
	}
}
