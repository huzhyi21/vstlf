/************************************************************************
 MIT License

 Copyright (c) 2010 University of Connecticut

 Permission is hereby granted, free of charge, to any person obtaining
 a copy of this software and associated documentation files (the
 "Software"), to deal in the Software without restriction, including
 without limitation the rights to use, copy, modify, merge, publish,
 distribute, sublicense, and/or sell copies of the Software, and to
 permit persons to whom the Software is furnished to do so, subject to
 the following conditions:

 The above copyright notice and this permission notice shall be
 included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
***********************************************************************/

package edu.uconn.vstlf.shutil;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Date;

import edu.uconn.vstlf.config.Items;
import edu.uconn.vstlf.data.Calendar;
import edu.uconn.vstlf.data.message.DummyMsgHandler;
import edu.uconn.vstlf.data.message.MessageCenter;
import edu.uconn.vstlf.data.message.VSTLFMessage;
import edu.uconn.vstlf.data.message.VSTLFMsgLogger;
import edu.uconn.vstlf.database.perst.PerstPowerDB;

public class RunValidation {

	static String _USAGE = "USAGE:\n\tjava -jar uconn-vstlf.jar validate <lowBank> <highBank> <xmlFile> "+
	"\n\tjava -jar uconn-vstlf.jar validate <lowBank> <highBank> <xmlFile> \"<startDate yyyy/MM/dd>\" \"<endDate yyyy/MM/dd>\"" +
	 "\n\n\t lowBank, highBank in [0,11]:\tthe program will test ANN banks for the\n\t\t\t\t\toffsets in the specified interval" +
	 "\n\n\t xmlFile:\t\t\t5minute load file.  XML.\n\t\t\t\t\t(see manual for XML format) \n\n" +
	 "\tThe specified set of neural network banks will be validated over the\ntime period contained in 'xmlFile'." +
	 "It is assumed that the current directory\ncontains a folder called 'anns/'.  If the contents\n\n\t" +
	 "(some subset of {bank0.ann, bank1.ann, bank2.ann, ... , bank11.ann})\n\n" +
	 "include the '.ann' files corresponding to the set of banks to be trained.\n\n";

	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Calendar cal = Items.makeCalendar();
		String xmlFile = null; int lo=0, hi=0; Date st, ed;
		if (args.length != 3 && args.length != 5) { 	//check # of args
			System.out.println(_USAGE);
			return;
		}
		try{										//assign args
			xmlFile = args[2];
			if(!new File(xmlFile).exists())
				throw new FileNotFoundException(xmlFile);
			lo = Integer.parseInt(args[0]);
			hi = Integer.parseInt(args[1]);
			if(lo > hi || lo < 0 || hi > 11)
				throw new NumberFormatException();
		}
		catch(NumberFormatException e){
			System.out.println("Specified set of ANN banks is not valid. (lo > hi || lo < 0 || hi > 11)");
			return;
		}
		catch(FileNotFoundException e){
			System.out.println("'" + e.getMessage() + "' does not refer to real file.");
			return;
		}
		try {								//run stuff
			MessageCenter.getInstance().setHandler(new VSTLFMsgLogger("vstlf.log", new DummyMsgHandler()));
			MessageCenter.getInstance().init();
			String tfName = ".load5m.pod";
			File tempFile;
			while(true){
				tempFile = new File(tfName);
				if(tempFile.exists())
					tfName = "." + tfName;
				else
					break;
			}
			tempFile.deleteOnExit();
			PerstPowerDB ppdb;
			if (getExtensionName(xmlFile).compareTo("xml") == 0) {
				System.out.println("Extracting 5m load signal from "+ xmlFile);
				ppdb = PerstPowerDB.fromXML(tfName, 300, xmlFile);
			}
			else 
				ppdb = new PerstPowerDB(xmlFile, 300);
			ppdb.open();
			st = cal.endDay(ppdb.first("filt"));
			ed = cal.beginDay(ppdb.last("filt"));
			ppdb.close();
			
			if (args.length == 5) {
				Date strt = RunTraining.parseDate(args[3]);
				Date end = RunTraining.parseDate(args[4]);
				if (strt == null)
					System.err.println("Cannot parse date " + args[3]);
				else if (end == null)
					System.err.println("Cannot parse date " + args[4]);
				else {
					st = strt;
					ed = end;
				}
			}
			
			//double[][] result = edu.uconn.vstlf.batch.VSTLFTrainer.test(tfName, st, ed, lo, hi);
			System.out.println("Validation Complete\n\nMean Error in MW:");
			System.out.println("Bank\t5m\t10m\t15m\t20m\t25m\t30m\t35m\t40m\t45m\t50m\t55m\t1h");
			for(int i = lo;i<=hi;i++){
				System.out.format("\n%d:\t",i);
				//for(int j = 0;j<12;j++)
					//System.out.format("%.1f\t", result[i][j]);
			}
			MessageCenter.getInstance().put(new VSTLFMessage(VSTLFMessage.Type.EOF));
		} catch (Exception e) {
		    System.out.println(e.toString());
		    e.printStackTrace();
		    return;
		}
	}

	static public String getExtensionName(String fileName)
	{
		int mid = fileName.lastIndexOf('.');
		return fileName.substring(mid+1,fileName.length());
	}
}
