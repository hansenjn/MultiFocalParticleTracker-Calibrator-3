/***===============================================================================
 
 MFPT Calibrator 3 plugin

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation (http://www.gnu.org/licenses/gpl.txt )

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.
 
 See the GNU General Public License for more details.
 
 @author Jan N Hansen
 @copyright (C) 2019: Jan N Hansen
 
 For any questions please feel free to contact me (jan.hansen(at)uni-bonn.de).

==============================================================================**/
package multi_focal_pt_calib3;

import java.awt.event.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

import javax.swing.UIManager;
import ij.*;
import ij.gui.*;
import ij.io.*;
import ij.measure.*;
import ij.plugin.*;
import ij.text.TextPanel;
import multi_focal_pt_calib3.jnhsupport.*;

public class MFPTmain implements PlugIn, Measurements{
	//Name
		public static final String PLUGINNAME = "Multi-focal Particle Tracking - Calibrator 3";
		public static final String PLUGINVERSION = "v0.0.2";
		
		double xyCal = 1.0;	//0.34375 for 32x, 0.55 for 20x
		int maxRadius = 20;
				
		ProgressDialog progress;
		boolean done = false;
				
	@Override
	public void run(String arg) {
		/**&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
		GenericDialog
		&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&*/
		
		GenericDialog gd = new GenericDialog("mulit-focal particle tracking - calibrator 2");		
//		setInsets(top, left, bottom)
		gd.setInsets(0,0,0);	gd.addMessage(PLUGINNAME + ", version " + PLUGINVERSION + " (\u00a9 2018-" + constants.dateY.format(new Date()) + ")", constants.Head1);					
		gd.setInsets(10,0,0);	gd.addNumericField("xy calibration [um]", xyCal, 5);	
		gd.setInsets(10,0,0);	gd.addNumericField("Maximum radius for fitting (px)", maxRadius, 0);								
		gd.showDialog();			 	
	 	xyCal = gd.getNextNumber();	 		
		maxRadius = (int)gd.getNextNumber();
			
		if (gd.wasCanceled())return;
	  	
  		/**&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
		Initiate multi task management
		&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&*/
		try{
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		}catch(Exception e){}
		
		//select tasks
		OpenFilesDialog od = new OpenFilesDialog ();
		od.setLocation(0,0);
		od.setVisible(true);		
		od.addWindowListener(new java.awt.event.WindowAdapter() {
	        public void windowClosing(WindowEvent winEvt) {
//	        	IJ.log("Analysis canceled!");
	        	return;
	        }
	    });	
		while(od.done==false){
			 try{
				 Thread.currentThread().sleep(50);
		     }catch(Exception e){
		     }
		}
		
		int tasks = od.filesToOpen.size();
		String [] name = new String [tasks];
		String [] dir = new String [tasks];
		boolean tasksSuccessfull [] = new boolean [tasks];
		for(int task = 0; task < tasks; task++){
			name [task] = od.filesToOpen.get(task).getName();
			dir [task] = od.filesToOpen.get(task).getParent() + System.getProperty("file.separator");
			tasksSuccessfull [task] = false;
		}	
				
		//start progress dialog
		progress = new ProgressDialog(name, tasks);
		progress.setVisible(true);
		progress.addWindowListener(new java.awt.event.WindowAdapter() {
	        public void windowClosing(WindowEvent winEvt) {
	        	progress.stopProcessing();
	        	if(done==false){
	        		IJ.error("Script stopped...");
	        	}       	
	        	System.gc();
	        	return;
	        }
		});	
		
		/**&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
											Processing
		&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&*/

		// get image location
	    	OpenDialog oImage;
	    	String nameImage = "";
	    	String dirImage = "";
    		oImage = new OpenDialog("Open corresponding image", null);
    		progress.replaceBarText("Open corresponding image");
    		nameImage = oImage.getFileName();
	    	dirImage = oImage.getDirectory();
		
		//Initialize
			ImagePlus imp;	
			
			// get image and open
	    	imp = IJ.openImage(dirImage+nameImage);	
	    	imp.getCalibration().pixelHeight = xyCal;
	    	imp.getCalibration().pixelWidth = xyCal;
//			String homePath = FileSystemView.getFileSystemView().getHomeDirectory().getAbsolutePath();
			Date startDate;
			
		//Initialize for initial params
			int binnedDistanceMatrix [][] = this.getBinnedDistanceMatrix();
			double uncalibDistanceMatrix [][] = this.getUncalibDistanceMatrix();
			
		//Initialize Variables
				
		//processing
		tasking: for(int task = 0; task < tasks; task++){
			progress.updateBarText("in progress...");
			startDate = new Date();
			progress.clearLog();
			
			running: while(true){
				//Check if image is processable
				if(imp.getBitDepth()==24){
		    		progress.notifyMessage("ERROR: Multi focal analysis cannot be used for RGB images!", ProgressDialog.ERROR);
			  		break running;
		    	}				
				
				//determine save path
		    	String savePath;
		    	if(name[task].contains(".")){
					savePath = name[task].substring(0,name[task].lastIndexOf("."));
				}else{
					savePath = name[task];
				}
		    	savePath = dir [task] + savePath + "_MFPTc" + "_" + constants.df0.format(maxRadius*2+1);	
		    	
		    	/**
		    	 * PROCESSING
		    	 * */
				{					
					//read x,y, points
					ArrayList<MFPT3_CalibrationPoint> track = readTrackList(dir[task]+name[task],imp.getNFrames(),imp.getNSlices());
					if(track.equals(null))	break running;
					  	
					double px, pxi;
					double py, pyi;
					double sigma = 0.0;
					double intensities [][] = new double [1][1];
					double [] result;
					
					String out;
					
					filterParticles: for(int i = 0; i < track.size(); i++){
						progress.updateBarText("analyzing particle " + constants.df0.format(i+1) + "...");
						pxi = track.get(i).X();
						pyi = track.get(i).Y();
						for(int s = 0; s < imp.getNSlices(); s++){	
							for(int t = 0; t < imp.getNFrames(); t++){
								progress.updateBarText("analyzing particle " + constants.df0.format(i+1) + "... (s = " + s + " t = " + t + ")");
								intensities = new double [1][1];
								px = pxi;
								py = pyi;
								try{
									//GET INTENSITIES AND SIGMA	- REMOVE SIGMA FROM V 0.0.2 on
//									sigma = this.getSigma2(imp, (int)Math.round(px/imp.getCalibration().pixelWidth),
//											(int)Math.round(py/imp.getCalibration().pixelHeight),
//											binnedDistanceMatrix, uncalibDistanceMatrix, s, t);
									
//									intensities = getIntensitiesInRadius(imp, px, py, t, s, binnedDistanceMatrix, uncalibDistanceMatrix, 2*sigma);
									intensities = getIntensitiesInRadius(imp, px, py, t, s, binnedDistanceMatrix, uncalibDistanceMatrix, maxRadius);
								}catch(Exception e){
									out = "";
									for(int err = 0; err < e.getStackTrace().length; err++){
										out += " \n " + e.getStackTrace()[err].toString();
									}
									progress.notifyMessage("Exception in forming intensity profile " + constants.df6US.format(track.get(i).X()) 
										+ " Y " + constants.df6US.format(track.get(i).Y())
										+ " ID " + constants.df0.format(i)
										+ " T " + constants.df0.format(t)
										+ " S " + constants.df0.format(s) 
										+ "Cause: " + e.getCause()+ "\n"	
										+ "Throwable: " + e.getMessage() + "\n"	
										+ out, 
										ProgressDialog.LOG);
//									progress.notifyMessage("sigma " + sigma, ProgressDialog.LOG);
									
								}
								if(intensities.length == 1){
									progress.notifyMessage("Intensities could not be grabbed for " + constants.df6US.format(track.get(i).X()) 
									+ " Y " + constants.df6US.format(track.get(i).Y())
									+ " ID " + constants.df0.format(i)
									+ " T " + constants.df0.format(t)
									+ " S " + constants.df0.format(s) 
									+ "Sigma: " + sigma, ProgressDialog.LOG);
									continue;
								}
								try{
									result = MFPT_FitCircle_AG.getCenterRadiusAndR2(intensities [0], intensities [1], intensities [2]);
									track.get(i).setFitX(result[0], t, s);
									track.get(i).setFitY(result[1], t, s);
									track.get(i).setRadiusCentred(result[2], t, s);
									track.get(i).setR2Centred(result[3], t, s);
								}catch(Exception e){
									out = "";
									for(int err = 0; err < e.getStackTrace().length; err++){
										out += " \n " + e.getStackTrace()[err].toString();
									}
									progress.notifyMessage("Exception in fitting (with free centre) for particle X " + constants.df6US.format(track.get(i).X()) 
										+ " Y " + constants.df6US.format(track.get(i).Y())
										+ " ID " + constants.df0.format(i)
										+ " T " + constants.df0.format(t)
										+ " S " + constants.df0.format(s) 
										+ "Cause: " + e.getCause()+ "\n"	
										+ "Throwable: " + e.getMessage() + "\n"	
										+ out, 
										ProgressDialog.LOG);
									progress.notifyMessage("sigma " + sigma, ProgressDialog.LOG);
									for(int abc = 0; abc < intensities[0].length; abc++){
										progress.notifyMessage(abc + ": " + intensities[0][abc] + " / "
												+ intensities[1][abc] + " / " + intensities[2][abc], ProgressDialog.LOG);	
									}
								}
								
								try{									
									result = MFPT_FitCircle_AG.getRadiusAndR2(intensities [0], intensities [1], intensities [2], px, py);
									track.get(i).setRadius(result[0], t, s);
									track.get(i).setR2(result[1], t, s);						
								}catch(Exception e){
									out = "";
									for(int err = 0; err < e.getStackTrace().length; err++){
										out += " \n " + e.getStackTrace()[err].toString();
									}
									progress.notifyMessage("Exception in fitting (with fixed centre) for particle X " + constants.df6US.format(track.get(i).X()) 
										+ " Y " + constants.df6US.format(track.get(i).Y())
										+ " ID " + constants.df0.format(i)
										+ " T " + constants.df0.format(t)
										+ " S " + constants.df0.format(s) 
										+ "Cause: " + e.getCause()+ "\n"	
										+ "Throwable: " + e.getMessage() + "\n"	
										+ out, 
										ProgressDialog.LOG);
									progress.notifyMessage("sigma " + sigma, ProgressDialog.LOG);
									for(int abc = 0; abc < intensities[0].length; abc++){
										progress.notifyMessage(abc + ": " + intensities[0][abc] + " / "
												+ intensities[1][abc] + " / " + intensities[2][abc], ProgressDialog.LOG);	
									}
								}
							}							
						}
						progress.addToBar(0.5/(double)track.size());
					}					
					
					/**
					 * Center and output
					 * */
					this.centerAndOutputCentredRadii(imp, track, name[task], nameImage, startDate, savePath);
					this.plainOutput(imp, track, name[task], nameImage, startDate, savePath);
					
					System.gc();
				}	
				
				//save progress dialog log file
				  	progress.saveLog(savePath + "_log.txt");
				  
			  	//finish progress dialog				  	
				  	progress.setBar(1.0);
				  	done = true;
				  	tasksSuccessfull [task] = true;
				  	break running;		  	
			}//(end runnning)
			
			if(progress.isStopped()) break tasking;
			progress.moveTask(task);			
		}
		imp.changes = false;
	  	imp.close();
	}
	
	static void addFooter (TextPanel tp){
		tp.append("");
		tp.append("Datafile was generated by '"+PLUGINNAME+"', (\u00a9 2018-" + constants.dateY.format(new Date()) + ": Jan N Hansen (jan.hansen@uni-bonn.de))");
		tp.append("The plug-in '"+PLUGINNAME+"' is distributed in the hope that it will be useful,"
				+ " but WITHOUT ANY WARRANTY; without even the implied warranty of"
				+ " MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.");		
		tp.append("Plug-in version:	"+PLUGINVERSION);	  	
	}
	
	private ArrayList<MFPT3_CalibrationPoint> readTrackList(String filePath, int nrOfFrames, int nrOfSlices){
		ArrayList<MFPT3_CalibrationPoint> track = new ArrayList<MFPT3_CalibrationPoint>();
		track.ensureCapacity(400);
		{
			//read information about width max and min
			String px, py;
			try {
				FileReader fr = new FileReader(new File(filePath));
				BufferedReader br = new BufferedReader(fr);
				String line = "";							
				reading: while(true){
					try{
						line = br.readLine();	
						if(line.equals(null)){
							progress.updateBarText("track of " + track.size() + " points detected");
							break reading;
						}
					}catch(Exception e){
						progress.updateBarText("track of " + track.size() + " points detected");
						break reading;
					}					
					
					//check if comma instead of points and if so replace "," by "."
					px = (line.substring(0,line.lastIndexOf("	")));
					py = (line.substring(line.lastIndexOf("	")+1));
					if(px.contains(",") && !px.contains("."))	px = px.replace(",", ".");
					if(py.contains(",") && !py.contains("."))	py = py.replace(",", ".");
					track.add(new MFPT3_CalibrationPoint(Double.parseDouble(px),Double.parseDouble(py),nrOfFrames,nrOfSlices));							
				}					
				br.close();
				fr.close();
			}catch (IOException e) {
				IJ.error("Problem with text loading");
				e.printStackTrace();
				return null;
			}
		}
		track.trimToSize();
		return track;
	}
		
	/**
	 * Developed from 02.07. on
	 * @param centreX calibrated x centre position
	 * @param centreY calibrated y centre position
	 * @param slice >= 0 and < nrOfSlices
	 * @param frame >= 0 and < or nrOfFrames
	 * @returns 2D array: first dimension: 0 = x coord (calibrated), 1 = y coord (calibrated), 2 = intensity; 
	 * second dimension: different pixels
	 * */
	private double [][] getIntensitiesInRadius(ImagePlus imp, double centreX, double centreY, int frame, int slice, 
			int [][] binnedDistanceMatrix, double [][] uncalibDistanceMatrix,
			double uncalibRadius) {
		int px = (int)Math.round(centreX/imp.getCalibration().pixelWidth) - maxRadius;
		int py = (int)Math.round(centreY/imp.getCalibration().pixelHeight) - maxRadius;
		if(px >= (double)imp.getWidth()-1 || px < 0
				||py >= (double)imp.getHeight()-1 || py < 0){
			return new double [1][1];
		}	
		
		int counter = 0;
		for(int x = 0; x < binnedDistanceMatrix.length; x++){
			for(int y = 0; y < binnedDistanceMatrix[x].length; y++){
				if(binnedDistanceMatrix[x][y] <= maxRadius){
//					if(uncalibDistanceMatrix[x][y] <= uncalibRadius){	//From v 0.0.8 on				
						if(px+x < imp.getWidth() && py+y < imp.getHeight()){
							counter++;							
						}	
//					}										
				}				
			}
		}	
		
		double intensities [][] = new double [3][counter]; //x, y, z
		Arrays.fill(intensities[0], 0.0);
		Arrays.fill(intensities[1], 0.0);
		Arrays.fill(intensities[2], 0.0);
			
		counter = 0;
		for(int x = 0; x < binnedDistanceMatrix.length; x++){
			for(int y = 0; y < binnedDistanceMatrix[x].length; y++){
				if(binnedDistanceMatrix[x][y] <= maxRadius){
//					if(uncalibDistanceMatrix[x][y] <= uncalibRadius){	//From v 0.0.8 on				
						if(px+x < imp.getWidth() && py+y < imp.getHeight()){
							intensities[0][counter] = (double)(px+x)*imp.getCalibration().pixelWidth;
							intensities[1][counter] = (double)(py+y)*imp.getCalibration().pixelHeight;
							intensities[2][counter] = imp.getStack().getVoxel(px+x, py+y,imp.getStackIndex(1, slice+1, frame+1)-1);
							counter++;							
						}	
//					}										
				}				
			}
		}
		return intensities;		
	}
		
	private double [] normalizeToCenter(double input []){
		double output [] = new double [input.length*2+1];
		Arrays.fill(output, 0.0);
		
		//sliding median window across 5 steps
		double smoothed [] = new double [input.length];
		double window [] = new double [5];
		int counter;
		
		for(int i = 0; i < input.length; i ++){
			counter = 0;
			if(i > 1 && input [i-2] != 0.0){
				window [counter] = input [i-2];
				counter++;
			}
			if(i > 0 && input [i-1] != 0.0){
				window [counter] = input [i-1];
				counter++;
			}
			if(input [i] != 0.0){
				window [counter] = input [i];
				counter++;
			}			
			if(i < input.length-1 && input [i+1] != 0.0){
				window [counter] = input [i+1];
				counter++;
			}
			if(i < input.length-2 && input [i+2] != 0.0){
				window [counter] = input [i+2];
				counter++;
			}			
			if(counter == 0){
				smoothed [i] = 0.0;
			}else if(counter  == 1){
				smoothed [i] = window[0];
			}else{
				smoothed [i] = tools.getMedianOfRange(window, 0, counter-1);
			}			
		}
		
		//centering
		int minimumPosition = 0;
		double minimum = Double.POSITIVE_INFINITY;
		for(int i = 0; i < input.length; i ++){
			if(smoothed [i] != 0.0 && smoothed [i] < minimum){
				minimum = smoothed [i];
				minimumPosition = i;
			}
		}
		
		for(int i = 0; i < smoothed.length; i ++){
			output[input.length+i-minimumPosition] = smoothed[i];
		}		
		return output;
	}
	
	private double [] smoothCurve(double [] input){
		double [] output = new double [input.length];
		double newIntensity;
		int counter;
		for(int n = 0; n < input.length; n++){
			newIntensity = input[n];								
			if(n==0){
				if(input.length>1){
					if(input[n+1] != 0.0){
						newIntensity += input[n+1];
						newIntensity /= 2.0;
					}					
				}						
			}else if(n==input.length-1){
				if(input[n-1] != 0.0){
					newIntensity += input[n-1];
					newIntensity /= 2.0;
				}				
			}else{
				counter = 1;
				if(input[n+1]!=0.0){
					newIntensity += input[n+1];
					counter++;
				}
				if(input[n-1]!=0.0){
					newIntensity += input[n-1];
					counter++;
				}
				newIntensity /= (double)counter;
			}
			output[n] = newIntensity;	
		}
		return output;
	}
	
	private double [][][] normalizeMultiplaneToCenterByLeastMeanSquare2(double inArray [][][], ProgressDialog progress){	//slice,i,t
		int nSlices = inArray.length;
		int trackSize = inArray[0].length;
		int nFrames = inArray[0][0].length;
		int shiftrange = ((int)Math.round(nFrames/2)-1);
		double input [][][] = new double [nSlices][trackSize][nFrames];
		
		double maxDiv [] = new double [trackSize];
		Arrays.fill(maxDiv, Double.NEGATIVE_INFINITY);
		double minDiv [] = new double [trackSize];
		Arrays.fill(minDiv, Double.POSITIVE_INFINITY);
		
		for(int s = 0; s < nSlices; s++){
			for(int i = 0; i < trackSize; i++){
				//Smooth curve
				input[s][i] = smoothCurve(inArray[s][i]);
//				for(int t = 0; t < nFrames; t++){
//					if(input [s][i][t] != 0.0){
//						if(input [s][i][t] < minDiv [i])	minDiv [i] = input [s][i][t];
//						if(input [s][i][t] > maxDiv [i])	maxDiv [i] = input [s][i][t];
//					}
//				}	
			}
		}
				
		//filter out high values
//		double max = tools.getMedian(maxDiv), min = tools.getMedian(minDiv);
//		double threshold = min+(max-min)/3.0*2.0;
		double threshold = maxRadius * xyCal / 5.0 * 2.0;
		progress.notifyMessage("Threshold:	" + threshold, ProgressDialog.LOG);
		for(int s = 0; s < nSlices; s++){
			for(int i = 0; i < trackSize; i++){
				for(int t = 0; t < nFrames; t++){
					if(input [s][i][t] > threshold){
						input [s][i][t] = 0.0;
//					}else{
//						input [s][i][t] = (input [s][i][t]-min)/(max-min)*1000;	//TODO new from 02.05.2019
					}
				}	
			}
		}
		
//		progress.notifyMessage("output array dimensions" + nSlices + " - " + trackSize + " - " + nFrames+4*shiftrange+1, ProgressDialog.LOG);	
		double output [][][] = new double [nSlices][trackSize][nFrames+4*shiftrange+1];
		int shifts [][][] = new int [nSlices][trackSize][trackSize]; //[alignmentSeq][shifts]
		double lms [][][] = new double [nSlices][trackSize][trackSize];
		double minLMS, lowestTotalLMS = Double.POSITIVE_INFINITY, totalLMS = 0.0; 
		int minLMSShift, lowestTotalLMSIndex = 0, bestLMSIndexPlane0 = 0;
		double LMSResult [];
		
		for(int s = 0; s < nSlices; s++){
			for(int i = 0; i < trackSize; i++){
				Arrays.fill(output[s][i], 0.0);
				Arrays.fill(lms[s][i], 0.0);
				Arrays.fill(shifts[s][i], 0);
			}
			lowestTotalLMS = Double.POSITIVE_INFINITY;
			for(int a = 0; a < trackSize; a++){
				for(int b = 0; b < trackSize; b++){
//					if(b==a) continue;
					minLMS = Double.POSITIVE_INFINITY;
					minLMSShift = 0;
					for(int shift = -1*shiftrange; shift <= shiftrange; shift ++){
						LMSResult = leastMeanSquare(input[s][a], input[s][b], shift);
//						if (b==a && shift == 0) IJ.log(a + "=" + b + ":" + LMSResult [0] + " - " + LMSResult [1]);
						if(LMSResult [0] < minLMS 
								&& LMSResult [0] != Double.NEGATIVE_INFINITY 
								&& LMSResult [1] >= 0.5){
							minLMS = LMSResult [0];
							minLMSShift = shift;
						}
					}
					shifts[s][a][b] = minLMSShift;
					if(minLMS != Double.POSITIVE_INFINITY){
						lms[s][a][b] = minLMS;
					}else{
						lms[s][a][b] = sum(input[s][a]);
					}
				}				
				totalLMS = sum(lms[s][a]);
				if(totalLMS < lowestTotalLMS){
					lowestTotalLMS = totalLMS;
					lowestTotalLMSIndex = a;
				}
			}
			progress.notifyMessage("lowest LMS for plane " + s + ": " + lowestTotalLMS,ProgressDialog.LOG);
			progress.notifyMessage("best LMS index for plane " + s + ": " + lowestTotalLMSIndex,ProgressDialog.LOG);
			if(lowestTotalLMS != Double.POSITIVE_INFINITY){
				int start = shiftrange*2;
				if(s!=0){
					start += shifts[0][bestLMSIndexPlane0][lowestTotalLMSIndex];
				}else{
					bestLMSIndexPlane0 = lowestTotalLMSIndex;
				}
				for(int a = 0; a < trackSize; a++){
					progress.notifyMessage("plane " + s + ": shift " + a + ": " + shifts[s][lowestTotalLMSIndex][a] 
							+ " = " + (start+shifts[s][lowestTotalLMSIndex][a]) , ProgressDialog.LOG);
					for(int i = 0; i < nFrames; i ++){
						output[s][a][start+i+shifts[s][lowestTotalLMSIndex][a]] = inArray[s][a][i];
					}
				} 
			}
		}
		return output;
	}
	
	private static double [] leastMeanSquare (double f1 [], double f2 [], int indexShiftF2){
		double lms = 0.0;
		int counter = 0;
		int counterF1 = 0, counterF2 = 0;
		for(int i = 0; i < f1.length; i++){
			if(f1[i]!=0.0 && !Double.isNaN(f1[i])){
				counterF1++;
			}
		}
		for(int i = 0; i < f2.length; i++){
			if(f2[i]!=0.0 && !Double.isNaN(f2[i])){
				counterF2++;
			}
		}
		
		for(int i = 0; i < f1.length; i++){
			if(i-indexShiftF2 < 0 || i-indexShiftF2 >= f2.length){
				continue;
			}
//			if(f1[i]!=0.0){
//				counterF1++;
//			}
//			if(f2[i-indexShiftF2]!=0.0){
//				counterF2++;
//			}
			if(f1[i]==0.0 || f2[i-indexShiftF2] == 0.0){
				continue;
			}else{
				lms += Math.pow(f1[i]-f2[i-indexShiftF2],2.0);
				counter ++;
			}
		}
		double output [] = {lms,counter};
		if(counter == 0){
			output [0] = Double.NEGATIVE_INFINITY;
			return output;
		}
		if(counterF2<counterF1){
			output[1] /= (double) counterF2;
		}else{
			output[1] /= (double) counterF1;
		}		
		return output;
	}
	
	private static double sum (double data[]){
		double sum = 0.0;
		for(int i = 0; i < data.length; i++){
			sum += data[i];
		}
		return sum;
	}
	
	/**
	 * New from 2019-04-22	TODO
	 * */
	private int [][] getBinnedDistanceMatrix(){
		int matrix [][] = new int [(maxRadius*2+1)][(maxRadius*2+1)];		
		double distance;
		for(int x = 0; x < matrix.length; x++){
			for(int y = 0; y < matrix[x].length; y++){
				distance = Math.sqrt((x-maxRadius)*(x-maxRadius)+(y-maxRadius)*(y-maxRadius));
//				if(distance<=maxRadius){
					matrix [x][y] = (int)Math.round(distance);
//				}				
			}
		}
		
		return matrix;	
//		return tools.getMedianOfRange(values, firstIndex, lastIndex);
	}
	
	/**
	 * New from 2019-04-22	TODO
	 * */
	private double [][] getUncalibDistanceMatrix(){
		double matrix [][] = new double [(maxRadius*2+1)][(maxRadius*2+1)];
		double distance;
		for(int x = 0; x < matrix.length; x++){
//			appText = "";
			for(int y = 0; y < matrix[x].length; y++){
				distance = Math.sqrt(((double)x-(double)maxRadius)*((double)x-(double)maxRadius)
						+((double)y-(double)maxRadius)*((double)y-(double)maxRadius));
//				if(distance<=maxRadius){
				matrix [x][y] = distance;
//				}				
			}
		}
		
		return matrix;	
//		return tools.getMedianOfRange(values, firstIndex, lastIndex);
	}
		
	/**
	 * @param pointX uncalibrated x pixel position
	 * @param pointY uncalibrated y pixel position
	 * @param slice >= 0 and < nrOfSlices
	 * @param frame >= 0 and < or nrOfFrames
	 * */	
	private double getSigma2(ImagePlus imp, int pointX, int pointY, int binnedDistanceMatrix [][], 
			double uncalibDistanceMatrix [][], int slice, int frame){
		int px = pointX-maxRadius;
		int py = pointY-maxRadius;
		double intensities [] = new double [(maxRadius*2+1)*(maxRadius*2+1)];
		double integratedIntensity = 0.0;
		double intensity, max, min;
		Arrays.fill(intensities, Double.MAX_VALUE);
		int counter = 0;
		if(px >= (double)imp.getWidth()-1 || px < 0
				||py >= (double)imp.getHeight()-1 || py < 0){
			return Double.NaN;
		}	
		
		//Determine center of mass
//		for(int x = 0; x < binnedDistanceMatrix.length; x++){
//			for(int y = 0; y < binnedDistanceMatrix[x].length; y++){
//				if(binnedDistanceMatrix[x][y] <= maxRadius){
//					if(px+x < imp.getWidth() && py+y < imp.getHeight()){
//						intensity = imp.getStack().getVoxel(px+x, py+y, 
//								imp.getStackIndex(1, slice+1, frame+1)-1);
//						intensities [counter] = intensity;
//						counter ++;
//						integratedIntensity += intensity;
//					}
//				
//				}				
//			}
//		}
//		
//		Arrays.sort(intensities);
//		min = tools.getAverageOfRange(intensities, 0, (int)Math.round((double)counter/10.0)-1);
//		max = tools.getAverageOfRange(intensities, counter-(int)Math.round((double)counter/10.0)-1, counter-1);
//		
		double sigma = 0.0;
		integratedIntensity = 0;
		//Determine sigma
		for(int x = 0; x < binnedDistanceMatrix.length; x++){
			for(int y = 0; y < binnedDistanceMatrix[x].length; y++){
				if(binnedDistanceMatrix[x][y] <= maxRadius){
					if(px+x < imp.getWidth() && py+y < imp.getHeight()){
//					if((int)Math.round(xCenter)-maxRadius+x < imp.getWidth() && (int)Math.round(yCenter)-maxRadius+y < imp.getHeight()){
						intensity = imp.getStack().getVoxel(px+x, py+y, 
								imp.getStackIndex(1, slice+1, frame+1)-1);
//						intensity = imp.getStack().getVoxel((int)Math.round(xCenter)-maxRadius+x,
//								(int)Math.round(yCenter)-maxRadius+y, 
//								imp.getStackIndex(1, slice+1, frame+1)-1);
//						intensity -= backgroundMedianMin[slice][(int)Math.round(xCenter)-maxRadius+x][(int)Math.round(yCenter)-maxRadius+y];
//						intensity = (intensity-min)/(max-min);
						sigma += intensity * uncalibDistanceMatrix[x][y];
						integratedIntensity += intensity;						
					}
				}				
			}
		}
		sigma /= integratedIntensity;
		return sigma;
	}
		
	/**
	 * Determine tables for radius and save them
	 * New from 22.04.2019
	 * */
	private void centerAndOutputCentredRadii(ImagePlus imp, ArrayList<MFPT3_CalibrationPoint> track, String name, 
			String nameImage, Date startDate, String savePath){
		double centeredTable [][][] = new double [imp.getNSlices()][imp.getNFrames()*2+1][track.size()];
		double noncenteredTable [][][] = new double [imp.getNSlices()][track.size()][imp.getNFrames()];
		double averageCentered [][][] = new double [imp.getNSlices()][imp.getNFrames()*2+1][2]; // [][][0 = values, 1 = counter]
		for(int s = 0; s < imp.getNSlices(); s++){			
			for(int p = 0; p < imp.getNFrames()*2+1; p++){
				averageCentered [s][p][0] = 0.0;
				averageCentered [s][p][1] = 0.0;
			}
		}
		{
			double radiusValues [] = new double [imp.getNFrames()];
			double centered [];
			for(int s = 0; s < imp.getNSlices(); s++){			
				for(int i = 0; i < track.size(); i++){
					//get values
					Arrays.fill(radiusValues, 0.0);
					for(int t = 0; t < imp.getNFrames(); t++){
						radiusValues[t] = track.get(i).radiusCentred(t, s);
						noncenteredTable [s][i][t] = radiusValues[t];
					}
					
					//get centered results
					centered = this.normalizeToCenter(radiusValues);
					
					//write into table
					for(int p = 0; p < centered.length; p++){
						centeredTable [s][p][i] = centered [p];									
						if(centeredTable [s][p][i]>0.0){
							averageCentered [s][p][0] += centeredTable [s][p][i];
							averageCentered [s][p][1] += 1.0;
						}
					}
				}
//				IJ.log("ncT " + noncenteredTable[s].length + " - " + noncenteredTable[s][0].length + "/" + imp.getNFrames());							
			}
		}
		
		double LMSAligned [][][];
		LMSAligned = this.normalizeMultiplaneToCenterByLeastMeanSquare2(noncenteredTable, progress);	//slices, tracksize, length
//		boolean LMSAlignedAvailable [] = new boolean [LMSAligned[0][0].length];
//		Arrays.fill(LMSAlignedAvailable, false);
		int LMSAlignedPMin = -1, LMSAlignedPMax = LMSAligned[0][0].length;
		for(int t = 0; t < LMSAligned[0][0].length; t++){
			searching: for(int s = 0; s < LMSAligned.length; s++){			
				for(int i = 0; i < LMSAligned[0].length; i++){
					if(LMSAligned [s][i][t] != 0.0){
						if(LMSAlignedPMin == -1){
							LMSAlignedPMin = t;
						}
						LMSAlignedPMax = t;
						break searching;
					}
				}
			}						
		}
		
		//save into file
		String appText = "";
		TextPanel tp;
		tp = new TextPanel("Results");
		try{		  	
		  	tp.append("Saving date:	" + constants.dateTab.format(new Date()) + "	Analysis started:	" + constants.dateTab.format(startDate));
			tp.append("Processed file:	" + name);
			tp.append("Processed image:	" + nameImage);
			tp.append("");
			tp.append("Settings: ");
			tp.append("	" + "calibration (µm/px):	" + constants.df6US.format(xyCal));
			tp.append("	" + "maximum radius for fitting (px):	" + constants.df0.format(maxRadius));
			
			for(int s = 0; s < imp.getNSlices(); s++){
				tp.append("");
				tp.append("Radius results for plane " + constants.df0.format(s+1) + ":");
				appText = "T";
				for(int i = 0; i < track.size(); i++){
					appText += "	" + constants.df0.format(i+1);
				}
				tp.append(appText);
				for(int t = 0; t < imp.getNFrames(); t++){
					appText = constants.df0.format(t);
					for(int i = 0; i < track.size(); i++){
						appText += "	";
						if(track.get(i).radius(t, s)>0.0 && !track.get(i).skip){
							appText += constants.df6US.format(track.get(i).radius(t, s));
						}									
					}
					tp.append(appText);
					progress.addToBar(0.05/(double)track.size());
				}							
			}
			
			for(int s = 0; s < imp.getNSlices(); s++){
				tp.append("");
				tp.append("Centred radius results for plane " + constants.df0.format(s+1) + ":");
				appText = "Pos ";
				for(int i = 0; i < track.size(); i++){
					appText += "	" + constants.df0.format(i+1);
				}
				tp.append(appText);
				for(int p = 0; p < imp.getNFrames()*2+1; p++){
					appText = constants.df0.format(p - imp.getNFrames());
					for(int i = 0; i < track.size(); i++){
						appText += "	";
						if(centeredTable [s][p][i] > 0.0){
							appText += constants.df6US.format(centeredTable [s][p][i]);
						}									
					}
					tp.append(appText);
					progress.addToBar(0.05/(double)track.size());
				}							
			}	
			
			for(int s = 0; s < imp.getNSlices(); s++){
				tp.append("");
				tp.append("LMS aligned radius results for plane " + constants.df0.format(s+1) + ":");
				appText = "Pos ";
				for(int i = 0; i < track.size(); i++){
					appText += "	" + constants.df0.format(i+1);
				}
				tp.append(appText);
				for(int p = LMSAlignedPMin; p <= LMSAlignedPMax; p++){
					appText = constants.df0.format(p);
					for(int i = 0; i < LMSAligned[0].length; i++){
						appText += "	";
						if(LMSAligned [s][i][p] > 0.0){
							appText += constants.df6US.format(LMSAligned [s][i][p]);
						}									
					}
					tp.append(appText);
					progress.addToBar(0.05/(double)track.size());
				}							
			}
			
			{
				tp.append("");
				tp.append("Averaged centered radius results:");
				appText = "Pos ";
				for(int s = 0; s < imp.getNSlices(); s++){
					appText += "	" + constants.df0.format(s+1);
				}
				tp.append(appText);
				for(int p = 0; p < imp.getNFrames()*2+1; p++){
					appText = constants.df0.format(p - imp.getNFrames());
					for(int s = 0; s < imp.getNSlices(); s++){
						appText += "	";
						if(averageCentered [s][p][1] > 0.0){
							appText += constants.df6US.format(averageCentered [s][p][0]/averageCentered [s][p][1]);
						}									
					}
					tp.append(appText);
					progress.addToBar(0.04/(double)track.size());
				}							
			}						
			addFooter(tp);		
		  	tp.saveAs(savePath + "_r.txt");		
		}catch(Exception e){
			tp.saveAs(savePath + "_r.txt");		
			String out = "";
			for(int err = 0; err < e.getStackTrace().length; err++){
				out += " \n " + e.getStackTrace()[err].toString();
			}
			progress.notifyMessage("Outputting LMS for radius: an error occured: " + out
					+ "",ProgressDialog.ERROR);
			progress.notifyMessage("Outputting LMS for radius: error message: " + e.getMessage()
					+ "",ProgressDialog.ERROR);
			progress.notifyMessage("Outputting LMS for radius: cause: " + e.getCause()
					+ "",ProgressDialog.ERROR);
			tp = null;
			centeredTable = null;
			noncenteredTable = null;
			averageCentered = null;
			LMSAligned = null;
			System.gc();	
		}
		try{
			tp = new TextPanel("Results");
		  	tp.append("Saving date:	" + constants.dateTab.format(new Date()) + "	Analysis started:	" + constants.dateTab.format(startDate));
			tp.append("Processed file:	" + name);
			tp.append("Processed image:	" + nameImage);
			tp.append("");
			tp.append("Settings: ");
			tp.append("	" + "calibration (µm/px):	" + constants.df6US.format(xyCal));
			tp.append("	" + "maximum radius (px):	" + constants.df0.format(maxRadius));
			tp.append("");
			tp.append("Pos	LMS aligned radius results for plane:");
			
			appText = "	";
			for(int s = 0; s < imp.getNSlices(); s++){
				appText +=	"Plane " + constants.df0.format(s+1);
				if(s==imp.getNSlices()-1){
					break;
				}
				for(int i = 0; i < LMSAligned[0].length; i++){
					appText += "	";
				}
			}
			tp.append(appText);
			
			appText = "";
			for(int s = 0; s < imp.getNSlices(); s++){
				for(int i = 0; i < LMSAligned[0].length; i++){
					appText += "	" + constants.df0.format(i+1);
				}
			}
			tp.append(appText);
			
			for(int p = LMSAlignedPMin; p <= LMSAlignedPMax; p++){
				appText = constants.df0.format(p);
				for(int s = 0; s < imp.getNSlices(); s++){
					for(int i = 0; i < LMSAligned[0].length; i++){
						appText += "	";
						if(LMSAligned [s][i][p] != 0.0){
							appText += constants.df6US.format(LMSAligned [s][i][p]);
						}									
					}
				}							
				tp.append(appText);
			}
			addFooter(tp);
			tp.saveAs(savePath + "_r_LMS.txt");
		}catch(Exception e){
			tp.saveAs(savePath + "_r.txt");		
			String out = "";
			for(int err = 0; err < e.getStackTrace().length; err++){
				out += " \n " + e.getStackTrace()[err].toString();
			}
			progress.notifyMessage("Outputting LMS for radius: an error occured: " + out
					+ "",ProgressDialog.ERROR);
			progress.notifyMessage("Outputting LMS for radius: error message: " + e.getMessage()
					+ "",ProgressDialog.ERROR);
			progress.notifyMessage("Outputting LMS for radius: cause: " + e.getCause()
					+ "",ProgressDialog.ERROR);
			tp = null;
			centeredTable = null;
			noncenteredTable = null;
			averageCentered = null;
			LMSAligned = null;
			System.gc();	
		}
		tp = null;
		centeredTable = null;
		noncenteredTable = null;
		averageCentered = null;
		LMSAligned = null;
		System.gc();
	}
	
	private void plainOutput(ImagePlus imp, ArrayList<MFPT3_CalibrationPoint> track, String name, 
			String nameImage, Date startDate, String savePath){
		String appText = "";
		TextPanel tp;
		for(int part = 0; part < track.size(); part++){
			//save into file			
			tp = new TextPanel("Results");
			try{		  	
			  	tp.append("Saving date:	" + constants.dateTab.format(new Date()) + "	Analysis started:	" + constants.dateTab.format(startDate));
				tp.append("Processed file:	" + name);
				tp.append("Processed image:	" + nameImage);
				tp.append("Results for particle nr	" + constants.df0.format(part+1));
				tp.append("");
				tp.append("Settings: ");
				tp.append("	" + "calibration (µm/px):	" + constants.df6US.format(xyCal));
				tp.append("	" + "maximum radius for fitting (px):	" + constants.df0.format(maxRadius));
				
				for(int s = 0; s < imp.getNSlices(); s++){
					tp.append("");
					tp.append("Results for plane " + constants.df0.format(s+1) + ":");
					appText = "T";
					appText += "	" + "xCentre of fit";
					appText += "	" + "yCentre of fit";
					appText += "	" + "Radius of fit";
					appText += "	" + "R2 of fit";
					appText += "	" + "";
					appText += "	" + "Radius of fixed-centre fit";
					appText += "	" + "R2 of fixed-centre fit";
					
					tp.append(appText);
					for(int t = 0; t < imp.getNFrames(); t++){
						appText = constants.df0.format(t);
						appText += "	"; if(track.get(part).r2Centred(t, s)!=0.0)	appText += track.get(part).fitX(t, s);
						appText += "	"; if(track.get(part).r2Centred(t, s)!=0.0)	appText += track.get(part).fitY(t, s);
						appText += "	"; if(track.get(part).r2Centred(t, s)!=0.0)	appText += track.get(part).radiusCentred(t, s);
						appText += "	"; if(track.get(part).r2Centred(t, s)!=0.0)	appText += track.get(part).r2Centred(t, s);
						appText += "	"; appText += "";
						appText += "	"; if(track.get(part).r2(t, s)!=0.0)	appText += track.get(part).radius(t, s);
						appText += "	"; if(track.get(part).r2(t, s)!=0.0)	appText += track.get(part).r2(t, s);			
						tp.append(appText);
						progress.addToBar(0.05/(double)track.size());
					}							
				}				
				
				addFooter(tp);		
			  	tp.saveAs(savePath + "_r" + (part+1) + ".txt");		
			}catch(Exception e){
				tp.saveAs(savePath + "_r" + (part+1) + "_corrupted.txt");			
				String out = "";
				for(int err = 0; err < e.getStackTrace().length; err++){
					out += " \n " + e.getStackTrace()[err].toString();
				}
				progress.notifyMessage("Outputting LMS for particle" + (part+1) + ": an error occured: " + out
						+ "",ProgressDialog.ERROR);
				progress.notifyMessage("Outputting LMS for particle" + (part+1) + ": error message: " + e.getMessage()
						+ "",ProgressDialog.ERROR);
				progress.notifyMessage("Outputting LMS for particle" + (part+1) + ": cause: " + e.getCause()
						+ "",ProgressDialog.ERROR);
				tp = null;				
				System.gc();	
			}
		}
		
		
		tp = null;
		System.gc();
	}
}