package ba.patrickproppe.adb;
/*
 * This code is based on:
 * The previous bachelor thesis by Maximilian Peter Hackenschmied
 * https://github.com/mitchtech/android_adb_temp_light by Michael Mitchell
 * https://code.google.com/p/microbridge/ (MicroBridge LightWeight Server) by Niels Brouwers
*/
import java.io.IOException;

import javax.microedition.khronos.opengles.GL10;

import org.microbridge.server.AbstractServerListener;
import org.microbridge.server.Server;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import ba.patrickproppe.adb.Communicator.LocalBinder;

import com.panoramagl.PLIPanorama;
import com.panoramagl.PLImage;
import com.panoramagl.PLSpherical2Panorama;
import com.panoramagl.PLView;
import com.panoramagl.hotspots.PLHotspot;
import com.panoramagl.structs.PLRange;
import com.panoramagl.utils.PLUtils;

public class Periscope extends PLView {

	private String serverIP = "192.168.173.1"; // Sitzkiste
	
	protected final String TAG = Periscope.class.getSimpleName();

	PLHotspot hotspot1, hotspot2, hotspot3, hotspot1_activ, hotspot2_activ,
			hotspot3_activ, hotspot1_pic, hotspot2_pic, hotspot3_pic;
	PLIPanorama panorama = null;
	ImageView imageView = null;
	protected static final int PANO = R.raw.panorama2; // must be 2048x1024 !!
														// Coords:
														// 48.157148_11.537398

	protected static final int HOTSP1 = R.raw.hotspot;
	protected static final int HOTSP2 = R.raw.hotspot;
	protected static final int HOTSP3 = R.raw.hotspot;

	protected static final int HOTSP1_A = R.raw.hotspot_activated;
	protected static final int HOTSP2_A = R.raw.hotspot_activated;
	protected static final int HOTSP3_A = R.raw.hotspot_activated;

	protected static final int HOTSP1_PIC = R.raw.bmw;
	protected static final int HOTSP2_PIC = R.raw.frauenkirche;
	protected static final int HOTSP3_PIC = R.raw.nymphenburg;

	protected int pitch = 0, roll = 0, yaw = 0, freeze = 0, yaw_laststate = 0,
			counter = 0, direction = 1;
	float zoomFader_active = 0.0f, zoomFader_pic = 1.0f, zoomFader = 0.0f;
	protected long zoom_laststate = 0L, zoom = 0L;

	protected boolean isFreezeButton = false, isFreezeZoom = false;
	long lastTime, now, last_toggle;
	protected static final long UPDATETHRESHOLD = 20;
	protected static final int YAWTHRESHOLD = 1;
	protected static final float FOVMAX = 0.840f;
	protected static final float FOVMIN = -1.0f;
	protected static final float ZOOMSTEPS = 20.0f; // Anzahl Zoomstufen

	protected static final float HOTSP1_POS_DEG = 30.0f; // BMW
	protected static final float HOTSP2_POS_DEG = 160.0f; // Frauenkirche
	protected static final float HOTSP3_POS_DEG = 280.0f; // Schloss

	protected static final float HOTSP1_POS_YAW = -30.0f;
	protected static final float HOTSP2_POS_YAW = -160.0f;
	protected static final float HOTSP3_POS_YAW = 80.0f;

	protected static final float HOTSP_BLK_RANGE = 10.0f;
	protected static final int HOTSP_BLK_FREQ = 100;

	boolean hotspotsSetActive = false;
	boolean isHotsp1Active = false;
	boolean isHotsp2Active = false;
	boolean isHotsp3Active = false;
	boolean areHotspotsActive = false;
	boolean useCalibratedYaw = false;
	int yawOffset = 0, pitchOffset = 0;;

	float tempMax;
	// SERVER CODE:
	private String message;
	private static final String SPLIT_CHAR = ";";
	private Communicator connServ;
	private boolean mBound;
	private final Receiver receiver = new Receiver();
	private String registerKey = "Periscope";

	private int port = 4321;

	static final String calibrateYaw = "calibrateYaw";
	/**
	 * The component's ID
	 */
	static final String myID = "PERISCOPE";

	// END SERVER CODE

	// Create TCP server (based on MicroBridge LightWeight Server).
	// Note: This Server runs in a separate thread.
	Server adbServer = new Server(4568); // Use ADK port
	boolean serverStarted = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);

		this.showPanorama();
		getCamera().setFovRange(FOVMIN, FOVMAX);
		getCamera().setFov(FOVMIN);

		startClientService();

		// Create TCP server (based on MicroBridge LightWeight Server)
		try {

			adbServer.start();
			serverStarted = true;
		} catch (IOException e) {
			Log.e(TAG, "Unable to start TCP server", e);
			System.exit(-1);
		}

		adbServer.addListener(new AbstractServerListener() {

			@Override
			public void onReceive(org.microbridge.server.Client client,
					byte[] data) {

				Log.i(TAG, "data: " + data);
				final String dataStr = new String(data);

				try {
					// Any update to UI can not be carried out in a non UI
					// thread like the one used for Server.
					// Hence runOnUIThread is used.
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							new UpdateAllTask().execute(dataStr);
						}
					});
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});

	}

	// stops the Server when Activity is closed... prevent binding errors
	@Override
	public void onDestroy() {
		connServ.stopService();
		unregisterReceiver(receiver);

		if (serverStarted) {
			adbServer.stop();
			serverStarted = false;
		}

		// Unbind from the service
		if (mBound) {
			connServ.stopService();
			unbindService(mConnection);
			mBound = false;
		}
		super.onDestroy();
	}

	private void initHotspots() {

		hotspot1 = new PLHotspot(1, PLImage.imageWithBitmap(
				PLUtils.getBitmap(this, HOTSP1), false), 0.0f, HOTSP1_POS_DEG,
				0.13f, 0.13f);

		hotspot2 = new PLHotspot(2, PLImage.imageWithBitmap(
				PLUtils.getBitmap(this, HOTSP2), false), 0.0f, HOTSP2_POS_DEG,
				0.13f, 0.13f);

		hotspot3 = new PLHotspot(3, PLImage.imageWithBitmap(
				PLUtils.getBitmap(this, HOTSP3), false), 0.0f, HOTSP3_POS_DEG,
				0.13f, 0.13f);
		hotspot1_activ = new PLHotspot(1, PLImage.imageWithBitmap(
				PLUtils.getBitmap(this, HOTSP1_A), false), 0.0f,
				HOTSP1_POS_DEG, 0.13f, 0.13f);

		hotspot2_activ = new PLHotspot(2, PLImage.imageWithBitmap(
				PLUtils.getBitmap(this, HOTSP2_A), false), 0.0f,
				HOTSP2_POS_DEG, 0.13f, 0.13f);

		hotspot3_activ = new PLHotspot(3, PLImage.imageWithBitmap(
				PLUtils.getBitmap(this, HOTSP3_A), false), 0.0f,
				HOTSP3_POS_DEG, 0.13f, 0.13f);

		hotspot1_pic = new PLHotspot(1, PLImage.imageWithBitmap(
				PLUtils.getBitmap(this, HOTSP1_PIC), false), 0.0f,
				HOTSP1_POS_DEG, 0.13f, 0.13f);

		hotspot2_pic = new PLHotspot(2, PLImage.imageWithBitmap(
				PLUtils.getBitmap(this, HOTSP2_PIC), false), 0.0f,
				HOTSP2_POS_DEG, 0.13f, 0.13f);

		hotspot3_pic = new PLHotspot(3, PLImage.imageWithBitmap(
				PLUtils.getBitmap(this, HOTSP3_PIC), false), 0.0f,
				HOTSP3_POS_DEG, 0.13f, 0.13f);

		hotspot1.setZ(1f);
		hotspot2.setZ(1f);
		hotspot3.setZ(1f);

		hotspot1_activ.setZ(1f);
		hotspot2_activ.setZ(1f);
		hotspot3_activ.setZ(1f);

		hotspot1_pic.setZ(1f);
		hotspot2_pic.setZ(1f);
		hotspot3_pic.setZ(1f);

		panorama.addHotspot(hotspot1);
		panorama.addHotspot(hotspot2);
		panorama.addHotspot(hotspot3);

		panorama.addHotspot(hotspot1_activ);
		panorama.addHotspot(hotspot2_activ);
		panorama.addHotspot(hotspot3_activ);

		panorama.addHotspot(hotspot1_pic);
		panorama.addHotspot(hotspot2_pic);
		panorama.addHotspot(hotspot3_pic);

	}

	// This is triggered by onReceive()
	class UpdateAllTask extends AsyncTask<String, Integer, String> {

		// Called to initiate the background activity
		@Override
		protected String doInBackground(String... params) {
			String returnString = String.valueOf(params[0]);
			return (returnString); // This goes straight to result
		}

		// Called once the background activity has completed
		@Override
		protected void onPostExecute(String result) {
			now = System.currentTimeMillis();

			if (now - lastTime > UPDATETHRESHOLD) {
				try {
					String[] tmpstr = result.split(",");
					roll = Integer.parseInt(tmpstr[0]);
					pitch = Integer.parseInt(tmpstr[1]);
					yaw = Integer.parseInt(tmpstr[2]);
					zoom = Long.parseLong(tmpstr[3]);
					freeze = Integer.parseInt(tmpstr[4]);
				} catch (Exception e) {
					e.printStackTrace();
				}

				useADBData();

			}

			lastTime = System.currentTimeMillis();
		}
	}

	public void useADBData() {

		if (freeze == 1) {
			isFreezeButton = true;
		} else if (freeze == 0) {
			isFreezeButton = false;

		}

		if (zoom_laststate < zoom && zoom - zoom_laststate >= 2) {

			if (zoomFader <= 0.0f) {
				zoomIn(true);
				zoomIn(true);
			}

			if (getCamera().getFov() >= getCamera().getFovRange().max - 0.13f
					&& areHotspotsActive) {

				if (zoomFader < 1.0f) {
					zoomFader += 0.05f;
				}
				isFreezeZoom = true;

				zoomFader_pic = zoomFader;
				zoomFader_active = 1.0f - zoomFader;

				setHotspotAlpha("activated", zoomFader_active);
				setHotspotAlpha("picture", zoomFader_pic);
				showHotspots("picture", true);

			} else {
				// not in fade-range
				isFreezeZoom = false;
				zoomFader = 0.0f;
				showHotspots("picture", false);

			}

		} else if (zoom_laststate > zoom && zoom_laststate - zoom >= 2) {

			if (zoomFader <= 0.0f) {
				zoomOut(true);
				zoomOut(true);
			}

			if (getCamera().getFov() >= getCamera().getFovRange().max - 0.13f) {

				if (zoomFader > 0.0f) {
					zoomFader -= 0.05f;
				}

				isFreezeZoom = true;

				zoomFader_pic = zoomFader;
				zoomFader_active = 1.0f - zoomFader;

				setHotspotAlpha("activated", zoomFader_active);
				setHotspotAlpha("picture", zoomFader_pic);
				// showHotspots("picture", true);

			} else {
				// not in fade-range
				isFreezeZoom = false;
				zoomFader = 0.0f;

				showHotspots("picture", false);

			}

		}

		if (zoom_laststate - zoom >= 2) {

			zoom_laststate = zoom;
		} else if (zoom - zoom_laststate >= 2) {

			zoom_laststate = zoom;
		}

		// set only freeze when a hotspot is active
		boolean freezeZoom = areHotspotsActive && isFreezeZoom;

		if ((isFreezeButton && !freezeZoom) || (isFreezeButton && freezeZoom)
				|| (!isFreezeButton && freezeZoom)) {

		} else {

			// use calibrated Yaw if set
			yaw -= yawOffset;
			if (yaw < 0) {
				yaw = 360 + yaw;
			} else if (yaw > 360) {
				yaw = yaw - 360;
			}

			// convert to +-yaw-range
			if (yaw >= 180) {
				// negative yaw
				yaw = 360 - yaw;
				yaw = -yaw;

			} else if (yaw <= 180) {
				// positve yaw - do nothing
			}

			// use calibrated Pitch if set
			if (pitch > 0) {
				pitch -= pitchOffset;
			} else if (pitch < 0) {
				pitch += pitchOffset;

			}
			// take care that Pitch is in range (arduino-sensor)
			if (pitch > 20) {
				pitch = 20;
			} else if (pitch < -20) {
				pitch = -20;
			}

			// Pitch is negative!!!
			getCamera().lookAt(-pitch, (yaw*direction));

		}

		setHotspotsState();
	}

	/**
	 * This event is fired when OpenGL renderer was created
	 * 
	 * @param gl
	 *            Current OpenGL context
	 */
	@Override
	protected void onGLContextCreated(GL10 gl) {
		super.onGLContextCreated(gl);

		// Add layout
		View mainView = this.getLayoutInflater().inflate(R.layout.main, null);

		this.addContentView(mainView, new LayoutParams(
				LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));

	}

	/**
	 * Load panorama image manually
	 */
	private void showPanorama() {
		GL10 gl = this.getCurrentGL();

		// Lock panoramic view
		this.setBlocked(true);

		// Spherical2 panorama (supports only 2048x1024 texture)
		{
			panorama = new PLSpherical2Panorama();
			((PLSpherical2Panorama) panorama).setImage(gl, PLImage
					.imageWithBitmap(PLUtils.getBitmap(this, PANO), false));
		}

		// Add hotspots
		initHotspots(); // declare Hotspots

		setHotspotAlpha("normal", 1.0f); // setAlpha Hotspots
		setHotspotAlpha("activated", 1.0f); // setAlpha Hotspots_active
		setHotspotAlpha("picture", 0.0f); // setAlpha Hotspots_pic
		showHotspots("normal", true); // show Hotspots
		showHotspots("activated", false); // show Hotspots_active
		showHotspots("picture", false); // show Hotspots_pic

		// Load panorama
		this.reset();
		this.setPanorama(panorama);
		this.setAccelerometerEnabled(false);
		// Unlock panoramic view
		this.setBlocked(false);

	}

	private void showHotspots(String hotspot, boolean visible) {

		// PLHotspot(ID, Image, DEG_in_Y. DEG_in_X, width, height)

		if (hotspot == "normal") {

			hotspot1.setVisible(visible);
			hotspot2.setVisible(visible);
			hotspot3.setVisible(visible);

		} else if (hotspot == "activated") {

			hotspot1_activ.setVisible(visible);
			hotspot2_activ.setVisible(visible);
			hotspot3_activ.setVisible(visible);

		}

		if (hotspot == "picture") {

			hotspot1_pic.setVisible(visible);
			hotspot2_pic.setVisible(visible);
			hotspot3_pic.setVisible(visible);

		}

	}

	private void setHotspotAlpha(String hotspot, float alpha) {

		if (hotspot == "normal") {
			// Set alpha Value for normal Hotspots
			hotspot1.setOverAlpha(alpha);
			hotspot2.setOverAlpha(alpha);
			hotspot3.setOverAlpha(alpha);

			hotspot1.setDefaultAlpha(alpha);
			hotspot2.setDefaultAlpha(alpha);
			hotspot3.setDefaultAlpha(alpha);

			hotspot1.setDefaultOverAlpha(alpha);
			hotspot2.setDefaultOverAlpha(alpha);
			hotspot3.setDefaultOverAlpha(alpha);

			hotspot1.setAlpha(alpha);
			hotspot2.setAlpha(alpha);
			hotspot3.setAlpha(alpha);

		} else if (hotspot == "activated") {
			// Set alpha Value for activated Hotspots
			hotspot1_activ.setOverAlpha(alpha);
			hotspot2_activ.setOverAlpha(alpha);
			hotspot3_activ.setOverAlpha(alpha);

			hotspot1_activ.setDefaultAlpha(alpha);
			hotspot2_activ.setDefaultAlpha(alpha);
			hotspot3_activ.setDefaultAlpha(alpha);

			hotspot1_activ.setDefaultOverAlpha(alpha);
			hotspot2_activ.setDefaultOverAlpha(alpha);
			hotspot3_activ.setDefaultOverAlpha(alpha);

			hotspot1_activ.setAlpha(alpha);
			hotspot2_activ.setAlpha(alpha);
			hotspot3_activ.setAlpha(alpha);
		} else if (hotspot == "picture") {
			// Set alpha Value for pic Hotspots
			hotspot1_pic.setOverAlpha(alpha);
			hotspot2_pic.setOverAlpha(alpha);
			hotspot3_pic.setOverAlpha(alpha);

			hotspot1_pic.setDefaultAlpha(alpha);
			hotspot2_pic.setDefaultAlpha(alpha);
			hotspot3_pic.setDefaultAlpha(alpha);

			hotspot1_pic.setDefaultOverAlpha(alpha);
			hotspot2_pic.setDefaultOverAlpha(alpha);
			hotspot3_pic.setDefaultOverAlpha(alpha);

			hotspot1_pic.setAlpha(alpha);
			hotspot2_pic.setAlpha(alpha);
			hotspot3_pic.setAlpha(alpha);
		} else {

			// Set alpha Value for normal Hotspots
			hotspot1.setOverAlpha(alpha);
			hotspot2.setOverAlpha(alpha);
			hotspot3.setOverAlpha(alpha);

			hotspot1.setDefaultAlpha(alpha);
			hotspot2.setDefaultAlpha(alpha);
			hotspot3.setDefaultAlpha(alpha);

			hotspot1.setDefaultOverAlpha(alpha);
			hotspot2.setDefaultOverAlpha(alpha);
			hotspot3.setDefaultOverAlpha(alpha);

			hotspot1.setAlpha(alpha);
			hotspot2.setAlpha(alpha);
			hotspot3.setAlpha(alpha);

			// Set alpha Value for activated Hotspots
			hotspot1_activ.setOverAlpha(alpha);
			hotspot2_activ.setOverAlpha(alpha);
			hotspot3_activ.setOverAlpha(alpha);

			hotspot1_activ.setDefaultAlpha(alpha);
			hotspot2_activ.setDefaultAlpha(alpha);
			hotspot3_activ.setDefaultAlpha(alpha);

			hotspot1_activ.setDefaultOverAlpha(alpha);
			hotspot2_activ.setDefaultOverAlpha(alpha);
			hotspot3_activ.setDefaultOverAlpha(alpha);

			hotspot1_activ.setAlpha(alpha);
			hotspot2_activ.setAlpha(alpha);
			hotspot3_activ.setAlpha(alpha);

			// Set alpha Value for pic Hotspots
			hotspot1_pic.setOverAlpha(alpha);
			hotspot2_pic.setOverAlpha(alpha);
			hotspot3_pic.setOverAlpha(alpha);

			hotspot1_pic.setDefaultAlpha(alpha);
			hotspot2_pic.setDefaultAlpha(alpha);
			hotspot3_pic.setDefaultAlpha(alpha);

			hotspot1_pic.setDefaultOverAlpha(alpha);
			hotspot2_pic.setDefaultOverAlpha(alpha);
			hotspot3_pic.setDefaultOverAlpha(alpha);

			hotspot1_pic.setAlpha(alpha);
			hotspot2_pic.setAlpha(alpha);
			hotspot3_pic.setAlpha(alpha);

		}
	}

	protected boolean zoom(int direction, boolean animated) {
		boolean isZoomOut = (direction < 0);
		PLRange fovRange = getCamera().getFovRange();
		float fov = getCamera().getFov();

		if ((isZoomOut ? fov >= fovRange.min : fov <= fovRange.max)) {
			float distance = (fovRange.max - fovRange.min) / ZOOMSTEPS;
			if (animated) {
				float step = (isZoomOut ? -distance : distance) / 10.0f;
				for (int i = 0; i < 10; i++) {
					try {
						Thread.sleep(25);
					} catch (InterruptedException e) {
					}
					getCamera().setFov(getCamera().getFov() + step);
				}
			} else
				getCamera().setFov(getCamera().getFov() + distance);
			return true;
		}
		return false;
	}

	public boolean zoomIn(boolean animated) {
		return zoom(1, animated);
	}

	public boolean zoomOut(boolean animated) {
		return zoom(-1, animated);
	}

	// ServerCode

	public class Receiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			message = intent.getExtras().getString("command");
			interpret(message);
		}
	}

	private void interpret(String s) {
		String message = s;
		String[] m = new String[2];
		m = message.split(SPLIT_CHAR);
		if (message.contains(calibrateYaw)) {
			if (yawOffset == 0) {
				yawOffset = yaw;

			} else {
				yawOffset = 0;

			}

			if (pitchOffset == 0) {
				pitchOffset = pitch;

			} else {
				pitchOffset = 0;

			}
		} else if (message.contains("direction")) {
			if (direction == 1) {
				direction = -1;

			} else {
				direction = 1;

			}
		}

		else if (message.contains("getID")) {
			sendToServer("S;" + myID + ";sendID");
		}

		else if (message.contains("zoomIn")) {
			zoom += 2;
			useADBData();

		}

		else if (message.contains("zoomOut")) {
			zoom -= 2;
			useADBData();

		} else if (message.contains("resetView")) {
			PLRange x = getCamera().getPitchRange();
			sendToServer("Pitch Range: min:" + x.min + " max:" + x.max);

		} else if (message.contains("toggleAlpha")) {
			freeze = 1;
		}
	}

	private void toggleHotspotAlpha() {

	}

	private void toggleActivated(String type) {

		showHotspots(type, true);

	}

	private void toggleNormal() {

		showHotspots("normal", true);
		showHotspots("activated", false);

		// if (hotspotsSetActive) {
		// sendToServer("S;" + myID + ";Hotspots deactivated");
		// }
		hotspotsSetActive = false;

	}

	private void setHotspotsState() {

		float currentYaw = getCamera().getYaw();
		float currentYaw_sub = currentYaw - HOTSP_BLK_RANGE;
		float currentYaw_add = currentYaw + HOTSP_BLK_RANGE;

		isHotsp1Active = currentYaw_sub <= -HOTSP1_POS_YAW
				&& currentYaw_add >= -HOTSP1_POS_YAW;

		isHotsp2Active = currentYaw_sub <= -HOTSP2_POS_YAW
				&& currentYaw_add >= -HOTSP2_POS_YAW;

		isHotsp3Active = currentYaw_sub <= -HOTSP3_POS_YAW
				&& currentYaw_add >= -HOTSP3_POS_YAW;

		areHotspotsActive = isHotsp1Active || isHotsp2Active || isHotsp3Active;

		if (isHotsp1Active) {
			if (!hotspotsSetActive) {
				toggleActivated("activated");

				if (isFreezeZoom) {
					sendToServer("S;" + myID + ";sendHotspot;1");
					hotspotsSetActive = true;
				}
			} else {

				if (!isFreezeZoom) {
					sendToServer("S;" + myID + ";sendHotspot;0");
					hotspotsSetActive = false;
				}
			}

		} else if (isHotsp2Active) {

			if (!hotspotsSetActive) {
				toggleActivated("activated");

				if (isFreezeZoom) {
					sendToServer("S;" + myID + ";sendHotspot;2");
					hotspotsSetActive = true;

				}
			} else {

				if (!isFreezeZoom) {
					sendToServer("S;" + myID + ";sendHotspot;0");
					hotspotsSetActive = false;
				}
			}
		} else if (isHotsp3Active) {

			if (!hotspotsSetActive) {
				toggleActivated("activated");

				if (isFreezeZoom) {
					sendToServer("S;" + myID + ";sendHotspot;3");
					hotspotsSetActive = true;

				}
			} else {

				if (!isFreezeZoom) {
					sendToServer("S;" + myID + ";sendHotspot;0");
					hotspotsSetActive = false;
				}
			}
		}

		else {

			toggleNormal();

		}

	}

	private void startClientService() {
		IntentFilter ifi = new IntentFilter(registerKey);
		registerReceiver(receiver, ifi);

		Intent communicatorService = new Intent(this, Communicator.class);

		communicatorService.putExtra("url", serverIP);
		communicatorService.putExtra("port", port);
		communicatorService.putExtra("registerKey", registerKey);

		bindService(communicatorService, mConnection, Context.BIND_AUTO_CREATE);
		startService(communicatorService);
	}

	/** Defines callbacks for service binding, passed to bindService() */
	private ServiceConnection mConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {

			LocalBinder binder = (LocalBinder) service;
			connServ = binder.getService();
			mBound = true;
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			mBound = false;
		}
	};

	private void sendToServer(String message) {

		connServ.sendMsg(message);

	}

}
