/*
 * Copyright 2011 Marcy Gordon
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.opentripplanner.android;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.miscwidgets.widget.Panel;
import org.opentripplanner.api.model.EncodedPolylineBean;
import org.opentripplanner.api.model.Leg;
import org.opentripplanner.api.model.WalkStep;
import org.opentripplanner.api.ws.Request;
import org.opentripplanner.api.ws.Response;
import org.osmdroid.util.GeoPoint;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import de.mastacode.http.Http;

public class TripRequest extends AsyncTask<Request, Integer, Long> {
	private Response response;
	private static final String TAG = "OTP";
	private ProgressDialog progressDialog;
	private MainActivity activity;
	private Panel directionPanel;

	public TripRequest(MainActivity activity) {
		this.activity = activity;
		progressDialog = new ProgressDialog(activity);
	}

	protected void onPreExecute() {
		progressDialog = ProgressDialog.show(activity, "",
				"Generating trip. Please wait... ", true);
	}

	protected Long doInBackground(Request... reqs) {
		int count = reqs.length;
		long totalSize = 0;
		for (int i = 0; i < count; i++) {
			// totalSize += Downloader.downloadFile(reqs[i]);
			response = requestPlan(reqs[i]);
			// publishProgress((int) ((i / (float) count) * 100));
		}
		return totalSize;
	}

//	     protected void onProgressUpdate(Integer... progress) {
//	       //  setProgressPercent(progress[0]);
//	    	 setProgress(progress[0]);
//	    	 //TODO - fix tag
//	    	 Log.v(TAG, "Progress: " + progress[0]);
//	     }

	protected void onPostExecute(Long result) {
		if (progressDialog.isShowing()) {
			progressDialog.dismiss();
		}
		
		if (response != null && response.getPlan() != null && response.getPlan().itinerary.get(0) != null) {
			List<Leg> legs = response.getPlan().itinerary.get(0).legs;
			Log.v(TAG, "(TripRequest) legs size = "+Integer.toString(legs.size()));
			if (!legs.isEmpty()) {
				OTPPathOverlay otpPath = activity.routeOverlay;
				otpPath.removeAllPath();
				int index = 0;
				for (Leg leg : legs) {
					int pathColor = getPathColor(leg.mode);
					otpPath.addPath(pathColor);
					List<GeoPoint> points = EncodedPolylineBean
							.decodePoly(leg.legGeometry.getPoints());
//					Log.v(TAG, "(TripRequest) points size = "+Integer.toString(points.size())
//								+ " mode = "+leg.mode+" agencyId = "+leg.agencyId);
					for (GeoPoint geoPoint : points) {
						otpPath.addPoint(index, geoPoint);
					}
					index++;
				}
				
				showDirectionText(legs);
			}
		} else {
			// TODO - handle errors here?
			if(response != null && response.getError() != null) {
				String msg = response.getError().getMsg();
				AlertDialog.Builder feedback = new AlertDialog.Builder(activity);
				feedback.setTitle("Error Planning Trip");
				feedback.setMessage(msg);
				feedback.setNeutralButton("OK", null);
				feedback.create().show();
			}
			Log.e(TAG, "No route to display!");
		}
	}
	
	private int getPathColor(String mode){
		if(mode.equalsIgnoreCase("WALK")){
			return Color.DKGRAY;
		} else if(mode.equalsIgnoreCase("BUS")){
			return Color.RED;
		} else if(mode.equalsIgnoreCase("TRAIN")) {
			return Color.YELLOW;
		} else if(mode.equalsIgnoreCase("BICYCLE")){
			return Color.BLUE;
		}
		return Color.WHITE;
	}
	
	
	
	//function that removes underscores 
		private String turnsStr(String str){
			String temp="";
			if(str.equalsIgnoreCase("HARD_LEFT"))
				temp="HARD LEFT";
			else if(str.equalsIgnoreCase("SLIGHTLY_LEFT	"))
				temp="SLIGHTLY LEFT	";
			else if(str.equalsIgnoreCase("SLIGHTLY_RIGHT"))
				temp="SLIGHTLY RIGHT";
			else if(str.equalsIgnoreCase("HARD_RIGHT"))
				temp="HARD RIGHT";
			else if(str.equalsIgnoreCase("CIRCLE_CLOCKWISE"))
				temp="CIRCLE CLOCKWISE";
			else if(str.equalsIgnoreCase("CIRCLE_CLOCKWISE"))
				temp="CIRCLE CLOCKWISE";
			else if(str.equalsIgnoreCase("CIRCLE_COUNTERCLOCKWISE"))
				temp="CIRCLE COUNTERCLOCKWISE";
			else
				temp=str;
			return temp;
		}
	
	private void showDirectionText(List<Leg> legs){
		directionPanel = activity.directionPanel;
		TextView txtView=activity.txtDirections;
		
		
		String directionText = "Directions \n\n";
		String relativeStr="";
		int k=1;
		int flagRelative=0;
		
		for(Leg leg: legs){
			//zero variables for next iteration
			flagRelative=0;
			//TODO Might be better to compact some ifs (many ifs have exactly the same code in them)
			
			//if mode is WALK
			if(leg.mode.toString().equalsIgnoreCase("WALK")){
				
				for(WalkStep wStep: leg.walkSteps ){
					if (wStep.relativeDirection!=null){
						flagRelative=1;
						 if(!wStep.relativeDirection.toString().equalsIgnoreCase("CONTINUE")){
							 relativeStr=turnsStr(wStep.relativeDirection.toString());
							 directionText+= (k++) +". Turn " + relativeStr +" ";
						 }
						 else{
							 directionText+=(k++)+wStep.relativeDirection.toString()+" ";
						 }					 
					}
									
					if(wStep.absoluteDirection!=null && flagRelative==0){
						 
					     directionText+= (k++) + ". Walk "+wStep.absoluteDirection.toString()+ " "  ;
					    
					}
					
					directionText+="to " + wStep.streetName.toString() + " " + "\n"; 
				}	
			}
			//if mode is BICYCLE
			if(leg.mode.toString().equalsIgnoreCase("BICYCLE")){
				for(WalkStep wStep: leg.walkSteps ){
					if (wStep.relativeDirection!=null){
						flagRelative=1;
						 if(!wStep.relativeDirection.toString().equalsIgnoreCase("CONTINUE")){
							 relativeStr=turnsStr(wStep.relativeDirection.toString());
							 directionText+= (k++) +". Turn " + relativeStr +" ";
						 }
						 else
							 directionText+=(k++)+wStep.relativeDirection.toString()+" ";
					}
									
					if(wStep.absoluteDirection!=null && flagRelative==0){
					     directionText+= (k++) +". Bike "+wStep.absoluteDirection.toString()+ " "  ;   
					}
					directionText+="to " + wStep.streetName.toString() + " " + "\n"; 
				}
			}
			
			//if mode is BUS
			//need to add the departure time
			if(leg.mode.toString().equalsIgnoreCase("BUS")){
			
				directionText+= (k++) +". Take "+ leg.mode.toString() + " " + leg.route + " to "+leg.headsign + ".\nDrop off at "+ leg.to.name.toString() + "\n";
	
			}
			
			if(leg.mode.toString().equalsIgnoreCase("TRAIN") ||
			   leg.mode.toString().equalsIgnoreCase("TRAM") ||
			   leg.mode.toString().equalsIgnoreCase("Car") ||
			   leg.mode.toString().equalsIgnoreCase("SUBWAY") ||
			   leg.mode.toString().equalsIgnoreCase("RAIL") ||		
			   leg.mode.toString().equalsIgnoreCase("FERRY") ||
			   leg.mode.toString().equalsIgnoreCase("CABLE_CAR") ||
			   leg.mode.toString().equalsIgnoreCase("GONDOLA") ||
			   leg.mode.toString().equalsIgnoreCase("FUNICULAR") ||
			   leg.mode.toString().equalsIgnoreCase("TRANSIT")	 ||
			   leg.mode.toString().equalsIgnoreCase("TRAINISH") ||
			   leg.mode.toString().equalsIgnoreCase("BUSISH")	||
			   leg.mode.toString().equalsIgnoreCase("BOARDING") ||
			   leg.mode.toString().equalsIgnoreCase("ALIGHTING") ||
			   leg.mode.toString().equalsIgnoreCase("TRANSFER") ||
			   leg.mode.toString().equalsIgnoreCase("STL") ){
			
					directionText+=(k++) +". Take "+ leg.mode.toString() + " " + leg.route + " to "+leg.headsign + ".\nDrop off at "+ leg.to.name.toString() + "\n";

			}
			

		}	
		
		Log.d(TAG, "DIRECTIONS   " + directionText);
		txtView.setText(directionText);
		
		
	}
	
	private Response requestPlan(Request requestParams) {
		HashMap<String, String> tmp = requestParams.getParameters();

		Collection c = tmp.entrySet();
		Iterator itr = c.iterator();

		String params = "";
		boolean first = true;
		while(itr.hasNext()){
			if(first) {
				params += "?" + itr.next();
				first = false;
			} else {
				params += "&" + itr.next();						
			}
		}
		
		//fromPlace=28.066192823902,-82.416927819827
		//&toPlace=28.064072155861,-82.41109133301
		//&arr=Depart
		//&min=QUICK
		//&maxWalkDistance=7600
		//&mode=WALK
		//&itinID=1
		//&submit
		//&date=06/07/2011
		//&time=11:34%20am
		
		//String u = "http://go.cutr.usf.edu:8083/opentripplanner-api-webapp/ws/plan?fromPlace=28.066192823902,-82.416927819827&toPlace=28.064072155861,-82.41109133301&arr=Depart&min=QUICK&maxWalkDistance=7600&mode=WALK&itinID=1&submit&date=06/07/2011&time=11:34%20am";
		//String u = "http://go.cutr.usf.edu:8083/opentripplanner-api-webapp/ws/plan" + params;
		
		String res = "/plan";
		
		OTPApp app = ((OTPApp) activity.getApplication());
		Server server = app.getSelectedServer();
		if (server == null) {
			//TODO - handle error for no server selected
			return null;
		}
		String u = server.getBaseURL() + res + params;
		
		//Below fixes a bug where the New York OTP server will whine
		//if doesn't get the parameter for intermediate places
		if(server.getRegion().equalsIgnoreCase("New York")) {
			u += "&intermediatePlaces=";
		}
		
		Log.d(TAG, "URL: " + u);
		
		HttpClient client = new DefaultHttpClient();
		String result = "";
		try {
			result = Http.get(u).use(client).header("Accept", "application/xml").header("Keep-Alive","timeout=60, max=100").charset("UTF-8").followRedirects(true).asString();
			Log.d(TAG, "Result: " + result);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		
		Serializer serializer = new Persister();

		Response plan = null;
		try {
			plan = serializer.read(Response.class, result);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			Log.e(TAG, e.getMessage());
			e.printStackTrace();
			return null;
		}
		//TODO - handle errors and error responses
		if(plan == null) {
			Log.d(TAG, "No response?");
			return null;
		}
		return plan;
	}
}
