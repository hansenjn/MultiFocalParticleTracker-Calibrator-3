package multi_focal_pt_calib3;

public class MFPT3_CalibrationPoint{
	private double x,y;
	private double [][] radiusTimecorse; //frame, plane
	private double [][] radiusTimecorseCentred; //frame, plane
	private double [][] fitX; //frame, plane
	private double [][] fitY; //frame, plane
	private double [][] r2; //frame, plane
	private double [][] r2Centred; //frame, plane
	
	public boolean skip = false;
	
	public MFPT3_CalibrationPoint(double px, double py, int nFrames, int nSlices){
		x = px;
		y = py;
		radiusTimecorse = new double [nFrames][nSlices];
		fitX = new double [nFrames][nSlices];
		fitY = new double [nFrames][nSlices];
		r2 = new double [nFrames][nSlices];
		r2Centred = new double [nFrames][nSlices];
		radiusTimecorseCentred = new double [nFrames][nSlices];
		for(int t = 0; t < nFrames; t++){
			for(int s = 0; s < nSlices; s++){
				radiusTimecorse [t][s] = Double.NEGATIVE_INFINITY;
				fitX [t][s] = Double.NEGATIVE_INFINITY;
				fitY [t][s] = Double.NEGATIVE_INFINITY;
				r2 [t][s] = Double.NEGATIVE_INFINITY;
				r2Centred [t][s] = Double.NEGATIVE_INFINITY;
				radiusTimecorseCentred [t][s] = Double.NEGATIVE_INFINITY;
			}			
		}
	}	
	
	public double X(){
		return x;
		
	}
	
	public double Y(){
		return y;
		
	}
	
	public double radius(int frame, int slice){
		if(radiusTimecorse[frame][slice]!=Double.NEGATIVE_INFINITY){
			return radiusTimecorse[frame][slice];
		}else{
			return 0.0;
		}	
	}
	
	void setRadius(double radius, int frame, int slice){
		radiusTimecorse[frame][slice] = radius;
	}
	
	public double radiusCentred (int frame, int slice){
		if(radiusTimecorseCentred [frame][slice]!=Double.NEGATIVE_INFINITY){
			return radiusTimecorseCentred [frame][slice];
		}else{
			return 0.0;
		}	
	}
	
	void setRadiusCentred (double radius, int frame, int slice){
		radiusTimecorseCentred [frame][slice] = radius;
	}
	
	public double fitX(int frame, int slice){
		if(fitX[frame][slice]!=Double.NEGATIVE_INFINITY){
			return fitX[frame][slice];
		}else{
			return 0.0;
		}	
	}
	
	void setFitX(double fitValue, int frame, int slice){
		fitX[frame][slice] = fitValue;
	}
	
	public double fitY(int frame, int slice){
		if(fitY[frame][slice]!=Double.NEGATIVE_INFINITY){
			return fitY[frame][slice];
		}else{
			return 0.0;
		}	
	}
	
	void setFitY(double fitValue, int frame, int slice){
		fitY[frame][slice] = fitValue;
	}
	
	public double r2(int frame, int slice){
		if(r2[frame][slice]!=Double.NEGATIVE_INFINITY){
			return r2[frame][slice];
		}else{
			return 0.0;
		}	
	}
	
	void setR2(double goodness, int frame, int slice){
		r2[frame][slice] = goodness;
	}
	
	public double r2Centred(int frame, int slice){
		if(r2Centred[frame][slice]!=Double.NEGATIVE_INFINITY){
			return r2Centred[frame][slice];
		}else{
			return 0.0;
		}	
	}
	
	void setR2Centred(double goodness, int frame, int slice){
		r2Centred[frame][slice] = goodness;
	}
	
}
