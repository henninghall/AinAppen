package gov.polisen.ainappen;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ListView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ExternalDBHandeler{

	String	webserver	= "http://christian.cyd.liu.se";
	String casesForUser = "http://christian.cyd.liu.se:1337/casesForUser/1";
	ListView caseListView;
	Context rootview;
	List<Case> externalCaseList;

	public ExternalDBHandeler(Activity activity) {
		this.rootview = activity;
	}

	/*
	 * 1. Synchronizing external and local databases.
	 * 2. Updates Case list view if caseListView argument is not null.
	 */
	public void syncDatabases(List<Case> localCaseList, int userID, ListView caseListView) {
		if (caseListView != null) this.caseListView = caseListView;
		new GrabURL().execute(casesForUser);
	}

	private class GrabURL extends AsyncTask<String, Void, String> {

		@Override
		protected String doInBackground(String... urls) {

			//TA BRORT NÄR DATABAS FUNKAR
			if (true) return "[{\"author\":1,\"modificationTime\":\"Apr 24, 2014 1:39:19 PM\",\"firstrevisioncaseid\":1,\"firstrevisiondeviceid\":1,\"classification\":1,\"status\":1,\"description\":\"Snatteri på skånskgatan, skåning misstänkt.\",\"deviceid\":1,\"caseid\":1}," +
			"{\"author\":3,\"modificationTime\":\"Apr 24, 2014 1:39:19 PM\",\"firstrevisioncaseid\":1,\"firstrevisiondeviceid\":1,\"classification\":2,\"status\":2,\"description\":\"Mjölkbonde attackerad av struts.\",\"deviceid\":3,\"caseid\":1}]";

			StringBuilder builder = new StringBuilder();
			HttpClient client = new DefaultHttpClient();
			HttpGet httpGet = new HttpGet(urls[0]);

			try {
				HttpResponse response = client.execute(httpGet);
				StatusLine statusLine = response.getStatusLine();
				int statusCode = statusLine.getStatusCode();
				if (statusCode == 200) {

					HttpEntity entity = response.getEntity();
					InputStream content = entity.getContent();
					BufferedReader reader = new BufferedReader(
							new InputStreamReader(content));
					String line;
					while ((line = reader.readLine()) != null) {
						builder.append(line);
					}

					return builder.toString();
				} else {
					Log.e("Getter", "Failed to download file");
				}
			} catch (ClientProtocolException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}

			return null;
		}

		@Override
		protected void onPostExecute(String result) {

			// Converting json to list of case objects
			String camelCasedJson = camelCase(result);
			List<Case> externalCaseList = new Gson().fromJson(camelCasedJson, new TypeToken<List<Case>>() {}.getType());

			// Example case on server doesn't contain every field
			externalCaseList = addMissedFields(externalCaseList);

			List<Case> mergedCaseList = syncWithLocalDB(externalCaseList);

			// Updates listview
			if (caseListView != null) {
				updateListView(mergedCaseList);
			}

			showToast("Synced with external DB.");
		}

		private List<Case> syncWithLocalDB(List<Case> externalCaseList) {

			// Gets alla local cases from local database
			LocalDBHandler ldbh = new LocalDBHandler(rootview);
			List<Case> localCaseList = ldbh.getCasesFromDB();

			// Merged Caselist started with all local cases.
			List<Case> mergedCaseList = localCaseList;

			boolean exists;
			for (Case eCase : externalCaseList) {
				exists = false;

				for (Case lCase : localCaseList) {
					// If a case with same id is found in local DB
					if (eCase.getCaseID() == lCase.getCaseID()
							&& eCase.getDeviceID() == lCase.getDeviceID()){
						exists = true;
						showToast("Local case: " + lCase.getModificationTime().toString());
						showToast("External case: " + eCase.getModificationTime().toString());


						// Update case in local db
						// if new case found in external db is newer version of local case.
						if (eCase.getModificationTime().after(lCase.getModificationTime())){
							ldbh.removeCaseFromDB(lCase);
							ldbh.addExistingCase(eCase);
						}

						// Update external db if new case found in external db
						//is older version of local case.
						else if (eCase.getModificationTime().before(lCase.getModificationTime())){
							// TODO: when post case method exist write this.
						}
					}
				}
				// If the external case doesnt exist in local DB
				if (!exists){
					ldbh.addExistingCase(eCase);
					mergedCaseList.add(eCase);
				}
			}
			ldbh.release();
			return mergedCaseList;
		}

		public List<Case> addMissedFields(List<Case> caseList) {
			for (Case c : caseList) {
				// TimeOfCrime is missing in external database why this temporary solution.
				if (c.getTimeOfCrime() == null) c.setTimeOfCrime(new Date());
				if (c.getPriority() == null) c.setPriority((short)1);
			}
			return caseList;
		}


		private void updateListView(List<Case> mergedCaseList) {
			CaseListAdapter adapter = new CaseListAdapter(rootview, mergedCaseList);
			caseListView.setAdapter(adapter);
		}

		private String camelCase(String casesJson) {
			String[][] replacements = {
					{"modificationtime", "modificationTime"},
					{"firstrevisioncaseid", "firstRevisionCaseId"},
					{"firstrevisioncaseid", "firstRevisionCaseId"},
					{"deviceid", "deviceID"},
					{"caseid", "caseID"},
					{"firstrevisioncaseid", "firstRevisionCaseID"},
					{"deviceid", "firstrevisiondeviceid"},
					{"deviceid", "deletiontime"},
					{"deviceid", "timeofcrime"},
			};

			//loop over the array and replace
			String strOutput = casesJson;
			for(String[] replacement: replacements) {
				strOutput = strOutput.replace(replacement[0], replacement[1]);
			}
			return strOutput;
		}

	}

	public void showToast(String text){
		Toast.makeText(rootview, text,
				Toast.LENGTH_SHORT).show();
	}
}

