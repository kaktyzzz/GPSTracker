package com.example.testapp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.text.format.Time;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.ToggleButton;


interface MLocationListener extends LocationListener {
	void getLink(MainActivity a);
	void unLink();
}

class LineCSV {
	
	private String line;
	private String delimiter;
	
	public LineCSV (String d) {
		delimiter = d;
		line = "";
	}
	public void add(String s) {
		line += s+delimiter;
	}
	
	public void add(double d) {
		line += Double.toString(d)+delimiter;
	}
	
	public String get() {
		return line.substring(0, line.length()-1)+"\n";
	}
}

class NoNConfObject {
	static LocationManager lm;
	static MLocationListener ll; 
}




public class MainActivity extends Activity{
	
	public TextView gpsLogView;
	public Button btnSend;
	public CheckBox cbAutoSend;
	public ToggleButton tbStartStop;
	
	public LocationManager mlocManager;
	public MLocationListener mlocListener;
	public Time now;
	
	public static final String LOG_TAG = "GPSLog";
	public static final String NET_TAG = "NetLog";
	public static final String logfile = "Log";
	public static final String server_adr = "http://192.168.1.227";
	public static final int GPSInterval = 30;
	public static final int NetInterval = 180;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		gpsLogView = (TextView) findViewById(R.id.gpslog);
		btnSend = (Button) findViewById(R.id.sendToServer);
		cbAutoSend = (CheckBox) findViewById(R.id.autosender);
		tbStartStop = (ToggleButton) findViewById(R.id.tbStartStop);
		now = new Time(Time.getCurrentTimezone());		
		
		NoNConfObject nco = (NoNConfObject) getLastNonConfigurationInstance();
		mlocManager = nco.lm;
		mlocListener = nco.ll;
		if (mlocListener !=null)
			mlocListener.getLink(this);
	}
	
	
	
	@Override
	public Object onRetainNonConfigurationInstance() {
		NoNConfObject nco = new NoNConfObject();
		nco.ll = mlocListener;
		nco.lm = mlocManager;
		if (mlocListener !=null)
			mlocListener.unLink();
		return nco;
	}
	
	
	
	public void GPSOn() {
		mlocListener = new MyLocationListener();
		mlocManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
		mlocManager.requestLocationUpdates( LocationManager.GPS_PROVIDER, GPSInterval*1000, 0, mlocListener);
		
	}
	
	public void GPSOff() {
		if (mlocManager != null)
			mlocManager.removeUpdates(mlocListener);
	}
	
	public void SendLog() {
		//запуск задачи: запрос на сервер
		NetTask nettask = new NetTask(this);
		nettask.execute();
	}
	
	public void SendTimer() {
		new CountDownTimer(NetInterval*1000,1000) {
			
			public void onTick(long millisUntilFinished) {
				
			}
			public void onFinish() {
				if (cbAutoSend.isChecked()) {
					SendLog();
					SendTimer();
				}
			}
		}.start();
	}
	
	public void btnSendClick(View view) {
		SendLog();
	}
	
	public void checkboxClick(View view) {
		//btnSend.setEnabled(!cbAutoSend.isChecked());
		SendTimer();
	}
	
	public void tbStartStopClick(View view) {
		if (tbStartStop.isChecked())
			GPSOn();
		else
			GPSOff();
	}
	
	
	public static class MyLocationListener implements MLocationListener {
		
		private MainActivity act;
		
		public void getLink(MainActivity a) {
			act = a;
		}
		
		public void unLink() {
			act = null;
		}
		
		@Override
		public void onLocationChanged(Location loc) {
			String MetricSpeed = loc.getSpeed()*3.6+"км/ч";
			act.now.setToNow();
			
			LineCSV linecsv = new LineCSV(",");
			linecsv.add(act.now.format("%Y-%m-%d %k:%M:%S"));
			linecsv.add(loc.getLatitude());
			linecsv.add(loc.getLongitude());
			linecsv.add(loc.getSpeed());
		
			String message = "["+act.now.format("%Y-%m-%d %k:%M:%S")+"] Ш:"+loc.getLatitude()+" Д:"+loc.getLongitude()+" Скорость:"+MetricSpeed+"\n";
			
			act.gpsLogView.setText(act.gpsLogView.getText()+message);
			Log.d(LOG_TAG, message);
			
			try {
				BufferedWriter bw = new BufferedWriter (new OutputStreamWriter(act.openFileOutput (logfile, Context.MODE_APPEND)));
				bw.write(linecsv.get());
				bw.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void onProviderDisabled(String provider) {
			
		}
	
		@Override
		public void onProviderEnabled(String provider) {
			
		}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {
			
		}
	}/* End of Class MyLocationListener */

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

}


class NetTask extends AsyncTask<Void,Void,Void> {
	
	public Context mc;
	
	public NetTask(Context c) {
		this.mc = c;
	}
	
	@Override
	protected void onPreExecute() {
		super.onPreExecute();
	}
	
	@Override
	protected Void doInBackground(Void...params) {
		HttpClient httpclient = new DefaultHttpClient();
		HttpPost httppost = new HttpPost(MainActivity.server_adr);
		
		String line;
		
		try {
			try {
				// читаем файл и шифруем строки в запрос
				List<NameValuePair> pairs = new ArrayList<NameValuePair>(2);
				BufferedReader br = new BufferedReader (new InputStreamReader(mc.openFileInput (MainActivity.logfile)));
			
				while ((line = br.readLine()) != null) {
					pairs.add(new BasicNameValuePair("GPSLog[]",line));
				}
			
				httppost.setEntity(new UrlEncodedFormEntity(pairs));
				br.close();
			} catch (IOException e) {
				Log.d(MainActivity.NET_TAG, "Файл отсутствует или пуст");
				return null;
			}
				
			HttpResponse response = httpclient.execute(httppost); //выполнение запроса
			//Log.d(LOG_TAG, Integer.toString(response.getStatusLine().getStatusCode()));
			if (response.getStatusLine().getStatusCode() == 200) {
				mc.deleteFile(MainActivity.logfile); //удаляем файл
				Log.d(MainActivity.NET_TAG, "OK");
			}
			
			//читаем ответ сервера
			BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent(),"UTF-8"));
			StringBuilder sb = new StringBuilder();
			line = null;
			while ((line = reader.readLine()) != null) {
				sb.append(line);
			}
			Log.d(MainActivity.NET_TAG, sb.toString());
				
		} catch (ClientProtocolException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		return null;
	}
	
	@Override
	protected void onPostExecute(Void result) {
		super.onPostExecute(result);
	}
}


