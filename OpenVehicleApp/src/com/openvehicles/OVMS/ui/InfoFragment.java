package com.openvehicles.OVMS.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.openvehicles.OVMS.R;
import com.openvehicles.OVMS.api.OnResultCommandListenner;
import com.openvehicles.OVMS.entities.CarData;
import com.openvehicles.OVMS.ui.utils.Ui;
import com.openvehicles.OVMS.ui.utils.Ui.OnChangeListener;
import com.openvehicles.OVMS.ui.witdet.ReversedSeekBar;
import com.openvehicles.OVMS.ui.witdet.ScaleLayout;
import com.openvehicles.OVMS.ui.witdet.SlideNumericView;
import com.openvehicles.OVMS.ui.witdet.SwitcherView;

public class InfoFragment extends BaseFragment implements OnClickListener,
		OnResultCommandListenner {
	private static final String TAG = "InfoFragment";

	private CarData mCarData;

	private AlertDialog chargerDialog;

	private boolean collectResults = false;

	public int maxRange;
	public int suffRange;
	public int suffSOC;
	public int etrAtSOC;
	public int etrSuffRange;
	public int etrSuffSOC;
	public int etrFull;



	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View rootView = inflater.inflate(R.layout.fragment_info, null);
		final ScaleLayout scaleLayout = (ScaleLayout) rootView
				.findViewById(R.id.scaleLayout);
		scaleLayout.setOnScale(new Runnable() {
			@Override
			public void run() {
				SeekBar sb = (SeekBar) scaleLayout
						.findViewById(R.id.tabInfoSliderChargerControl);
				ScaleLayout.LayoutParams lp = (ScaleLayout.LayoutParams) sb
						.getLayoutParams();

				Bitmap srcBmp = BitmapFactory
						.decodeResource(
								scaleLayout.getContext().getResources(),
								R.drawable.charger_button);
				int tw = (int) (srcBmp.getWidth() * (lp.height / srcBmp
						.getHeight()));
				int th = lp.height;

				if (tw < 40) {
					tw = 61;
				} // Sane lower limit
				if (th < 10) {
					th = 22;
				} // Sane lower limit

				Bitmap dstBmp = Bitmap.createScaledBitmap(srcBmp, tw,
						lp.height, true);
				srcBmp.recycle();

				BitmapDrawable drw = new BitmapDrawable(scaleLayout
						.getContext().getResources(), dstBmp);
				sb.setThumb(drw);
				// sb.setThumbOffset(dstBmp.getWidth() / 9);
			}
		});

		// request new ETR data:
		etrAtSOC = 0;

		return rootView;
	}

	@Override
	public void update(CarData pCarData) {

		mCarData = pCarData;

		// update UI:
		updateLastUpdatedView(pCarData);
		updateCarInfoView(pCarData);

		// Twizy and new SOC?
		if (mCarData.car_type.equals("RT") && (mCarData.car_soc_raw != etrAtSOC)) {
			// get max range, charge alerts + etr update:
			sendCommand("203", InfoFragment.this);
		}
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		findViewById(R.id.tabInfoTextSOC).setOnClickListener(this);
		findViewById(R.id.tabInfoTextChargeMode).setOnClickListener(this);
		findViewById(R.id.tabInfoImageBatteryChargingOverlay)
				.setOnClickListener(this);
		findViewById(R.id.tabInfoImageBatteryOverlay).setOnClickListener(this);

		ReversedSeekBar bar = (ReversedSeekBar) findViewById(R.id.tabInfoSliderChargerControl);
		bar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			private int mStartProgress;

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				int progress = seekBar.getProgress();
				if (progress > 50) {
					seekBar.setProgress(100);
					progress = 100;
				} else {
					seekBar.setProgress(0);
					progress = 0;
				}
				if (mStartProgress == progress)
					return;

				if (progress == 0)
					startCharge();
				else
					stopCharge();
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				mStartProgress = seekBar.getProgress();
			}

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
			}
		});

	}

	@Override
	public void onClick(View v) {
		chargerSetting();
	}

	@Override
	public void onResultCommand(String[] result) {

		if (result.length <= 1)
			return;

		int command = Integer.parseInt(result[0]);
		int rcode = Integer.parseInt(result[1]);

		if (mCarData.car_type.equals("RT") && (command == 201 || command == 202)) {
			// Renault Twizy: SetMaxRange / QueryMaxRange result
			if (result.length < 3)
				return;

			// get result data:
			maxRange = Integer.parseInt(result[2]);

			// update UI:
			if (collectResults == true) {
				collectResults = false;
				sendCommand("203", InfoFragment.this);
			} else {
				updateChargeAlerts();
			}
		}

		else if (mCarData.car_type.equals("RT") && (command == 203 || command == 204)) {
			// Renault Twizy: SetChargeAlerts / QueryChargeAlerts result
			if (result.length < 7)
				return;

			// get result data:
			suffRange = Integer.parseInt(result[2]);
			suffSOC = Integer.parseInt(result[3]);
			etrSuffRange = Integer.parseInt(result[4]);
			etrSuffSOC = Integer.parseInt(result[5]);
			etrFull = Integer.parseInt(result[6]);
			etrAtSOC = mCarData.car_soc_raw;

			// update UI:
			if (collectResults == true) {
				collectResults = false;
				sendCommand("201", InfoFragment.this);
			} else {
				updateChargeAlerts();
			}
		}

		else if (result.length >= 3) {
			// default: display first command result field
			Toast.makeText(getActivity(), result[2], Toast.LENGTH_SHORT).show();
		}

	}

	private void startCharge() {
		sendCommand(R.string.msg_starting_charge, "11", this);
		mCarData.car_charge_linevoltage_raw = 0;
		mCarData.car_charge_current_raw = 0;
		mCarData.car_charge_state_s_raw = "starting";
		mCarData.car_charge_state_i_raw = 0x101;
		updateCarInfoView(mCarData);
	}

	private void stopCharge() {
		sendCommand(R.string.msg_stopping_charge, "12", this);
		mCarData.car_charge_linevoltage_raw = 0;
		mCarData.car_charge_current_raw = 0;
		mCarData.car_charge_state_s_raw = "stopping";
		mCarData.car_charge_state_i_raw = 0x115;
		updateCarInfoView(mCarData);
	}

	private void chargerSetting() {
		if (mCarData.car_type.equals("RT"))
			chargerSettingRenaultTwizy();
		else
			chargerSettingDefault();
	}

	private void chargerSettingDefault() {
		View content = LayoutInflater.from(getActivity()).inflate(
				R.layout.dlg_charger, null);
		SwitcherView sw = (SwitcherView) content.findViewById(R.id.sv_state);
		sw.setOnChangeListener(new OnChangeListener<SwitcherView>() {
			@Override
			public void onAction(SwitcherView pData) {
				TextView txtInfo = (TextView) ((View) pData.getParent())
						.findViewById(R.id.txt_info);
				switch (pData.getSelected()) {
				case 2:
					txtInfo.setText(R.string.msg_charger_range);
					break;
				case 3:
					txtInfo.setText(R.string.msg_charger_perform);
					break;
				default:
					txtInfo.setText(null);
				}
			}
		});

		new AlertDialog.Builder(getActivity())
				.setTitle(R.string.lb_charger_setting)
				.setView(content)
				.setNegativeButton(R.string.Cancel, null)
				.setPositiveButton(android.R.string.ok,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface pDlg, int pWhich) {
								Dialog dlg = (Dialog) pDlg;
								SwitcherView sw = (SwitcherView) dlg
										.findViewById(R.id.sv_state);
								SlideNumericView snv = (SlideNumericView) dlg
										.findViewById(R.id.snv_amps);

								int ncm = sw.getSelected();
								if (ncm >= 2)
									ncm++;

								int ncl = snv.getValue();

								if (ncm != mCarData.car_charge_mode_i_raw
										&& ncl != mCarData.car_charge_currentlimit_raw) {
									sendCommand(
											R.string.msg_setting_charge_mc,
											String.format("16,%d,%d", ncm, ncl),
											InfoFragment.this);
								} else if (ncm != mCarData.car_charge_mode_i_raw) {
									sendCommand(R.string.msg_setting_charge_m,
											String.format("10,%d", ncm),
											InfoFragment.this);
								} else if (ncl != mCarData.car_charge_currentlimit_raw) {
									sendCommand(R.string.msg_setting_charge_c,
											String.format("15,%d", ncl),
											InfoFragment.this);
								}
							}
						}).show();
	}

	// Charger settings for Renault Twizy:
	// 	(charge alert setup)
	private void chargerSettingRenaultTwizy() {

		// open dialog:

		View content = LayoutInflater.from(getActivity()).inflate(
				R.layout.dlg_charger_twizy, null);

		chargerDialog = new AlertDialog.Builder(getActivity())
				.setTitle(R.string.lb_charger_setting_twizy)
				.setView(content)
				.setNegativeButton(R.string.Cancel, null)
				.setPositiveButton(android.R.string.ok,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface pDlg, int pWhich) {
								Dialog dlg = (Dialog) pDlg;

								SlideNumericView snvRange = (SlideNumericView) dlg
										.findViewById(R.id.snv_sufficient_range);
								SlideNumericView snvSOC = (SlideNumericView) dlg
										.findViewById(R.id.snv_sufficient_soc);

								int suffRange = snvRange.getValue();
								int suffSOC = snvSOC.getValue();

								// SetChargeAlerts (204):
								sendCommand(
										R.string.msg_setting_charge_alerts,
										String.format("204,%d,%d", suffRange, suffSOC),
										InfoFragment.this);
							}
						}).show();


		// request missing data:
		if (maxRange == 0) {
			collectResults = true;
			sendCommand("201", InfoFragment.this);
		}
		else if (etrAtSOC == 0) {
			collectResults = true;
			sendCommand("203", InfoFragment.this);
		}

		// ...or display directly:
		if (collectResults == false) {
			updateChargeAlerts();
		}

	}


	// load ChargeAlerts data into UI:
	public void updateChargeAlerts() {

		// update dialog if open:

		if (chargerDialog != null) {

			LinearLayout progressLayer = (LinearLayout) chargerDialog.findViewById(R.id.progress_layer);
			if (progressLayer != null && progressLayer.getVisibility() != View.GONE) {

				// add distance units to range label:
				TextView lbRange = (TextView) chargerDialog.findViewById(R.id.lb_sufficient_range);
				lbRange.setText(getString(R.string.lb_sufficient_range, mCarData.car_distance_units));

				// set range:
				SlideNumericView snvRange = (SlideNumericView) chargerDialog
						.findViewById(R.id.snv_sufficient_range);
				if (snvRange != null) {
					snvRange.init(0, maxRange, 1);
					snvRange.setValue(suffRange);
				}

				// set SOC:
				SlideNumericView snvSOC = (SlideNumericView) chargerDialog
						.findViewById(R.id.snv_sufficient_soc);
				if (snvSOC != null) {
					snvSOC.setValue(suffSOC);
				}

				// hide progress bar if all results collected:
				if (collectResults == false) {
					progressLayer.setVisibility(View.GONE);
				}
			}
		}


		// update main UI:

		String infoEtr = "";
		TextView textView;
		boolean etrVisible = false;

		if (etrSuffRange != 0) {
			String infoEtrRange = getString(R.string.info_etr_suffrange,
					suffRange, mCarData.car_distance_units,
					String.format("%02d:%02d", etrSuffRange / 60, etrSuffRange % 60));
			if (infoEtr.length() > 0)
				infoEtr += "\n";
			infoEtr += infoEtrRange;
		}

		if (etrSuffSOC != 0) {
			String infoEtrSOC = getString(R.string.info_etr_suffsoc,
					suffSOC,
					String.format("%02d:%02d", etrSuffSOC / 60, etrSuffSOC % 60));
			if (infoEtr.length() > 0)
				infoEtr += "\n";
			infoEtr += infoEtrSOC;
		}

		textView = (TextView) findViewById(R.id.tabInfoTextChargeEtrSuff);
		textView.setText(infoEtr);
		if (!infoEtr.equals("")) {
			etrVisible = true;
			textView.setVisibility(View.VISIBLE);
		} else {
			textView.setVisibility(View.INVISIBLE);
		}


		infoEtr = "";

		if (etrFull != 0) {
			String infoEtrFull = getString(R.string.info_etr_full,
					String.format("%02d:%02d", etrFull / 60, etrFull % 60));
			if (infoEtr.length() > 0)
				infoEtr += "\n";
			infoEtr += infoEtrFull;
		}

		textView = (TextView) findViewById(R.id.tabInfoTextChargeEtrFull);
		textView.setText(infoEtr);
		if (!infoEtr.equals("")) {
			etrVisible = true;
			textView.setVisibility(View.VISIBLE);
		} else {
			textView.setVisibility(View.INVISIBLE);
		}

		// display background if any ETR visible:
		ImageView bgImg = (ImageView) findViewById(R.id.tabInfoImageChargeEtr);
		bgImg.setVisibility(etrVisible ? View.VISIBLE : View.INVISIBLE);

	}


	// This updates the part of the view with times shown.
	// It is called by a periodic timer so it gets updated every few seconds.
	public void updateLastUpdatedView(CarData pCarData) {
		// Quick exit if the car data is not there yet...
		if ((pCarData == null) || (pCarData.car_lastupdated == null))
			return;

		// Let's update the Info tab view...

		// First the last updated section...
		TextView tv = (TextView) findViewById(R.id.txt_last_updated);
		long now = System.currentTimeMillis();
		long seconds = (now - pCarData.car_lastupdated.getTime()) / 1000;
		long minutes = (seconds) / 60;
		long hours = minutes / 60;
		long days = minutes / (60 * 24);
		Log.d(TAG, "Last updated: " + seconds + " secs ago");

		if (pCarData.car_lastupdated == null) {
			tv.setText("");
			tv.setTextColor(0xFFFFFFFF);
		} else if (minutes == 0) {
			tv.setText(getText(R.string.live));
			tv.setTextColor(0xFFFFFFFF);
		} else if (minutes == 1) {
			tv.setText(getText(R.string.min1));
			tv.setTextColor(0xFFFFFFFF);
		} else if (days > 1) {
			tv.setText(String.format(getText(R.string.ndays).toString(), days));
			tv.setTextColor(0xFFFF0000);
		} else if (hours > 1) {
			tv.setText(String
					.format(getText(R.string.nhours).toString(), hours));
			tv.setTextColor(0xFFFF0000);
		} else if (minutes > 60) {
			tv.setText(String.format(getText(R.string.nmins).toString(),
					minutes));
			tv.setTextColor(0xFFFF0000);
		} else {
			tv.setText(String.format(getText(R.string.nmins).toString(),
					minutes));
			tv.setTextColor(0xFFFFFFFF);
		}

		// Then the parking timer...
		tv = (TextView) findViewById(R.id.txt_parked_time);
		if ((!pCarData.car_started) && (pCarData.car_parked_time != null)) {
			// Car is parked
			tv.setVisibility(View.VISIBLE);
			seconds = (now - pCarData.car_parked_time.getTime()) / 1000;
			minutes = (seconds) / 60;
			hours = minutes / 60;
			days = minutes / (60 * 24);

			if (minutes == 0)
				tv.setText(getText(R.string.justnow));
			else if (minutes == 1)
				tv.setText("1 min");
			else if (days > 1)
				tv.setText(String.format(getText(R.string.ndays).toString(),
						days));
			else if (hours > 1)
				tv.setText(String.format(getText(R.string.nhours).toString(),
						hours));
			else if (minutes > 60)
				tv.setText(String.format(getText(R.string.nmins).toString(),
						minutes));
			else
				tv.setText(String.format(getText(R.string.nmins).toString(),
						minutes));
		} else {
			tv.setVisibility(View.INVISIBLE);
		}

		// The signal strength indicator
		ImageView iv = (ImageView) findViewById(R.id.img_signal_rssi);
		iv.setImageResource(Ui.getDrawableIdentifier(getActivity(),
				"signal_strength_" + pCarData.car_gsm_bars));
	}

	// This updates the main informational part of the view.
	// It is called when the server gets new data.
	public void updateCarInfoView(CarData pCarData) {
		TextView tv = (TextView) findViewById(R.id.txt_title);
		tv.setText(pCarData.sel_vehicle_label);

		tv = (TextView) findViewById(R.id.tabInfoTextSOC);
		tv.setText(pCarData.car_soc);

		TextView cmtv = (TextView) findViewById(R.id.tabInfoTextChargeMode);
		ImageView coiv = (ImageView) findViewById(R.id.tabInfoImageBatteryChargingOverlay);
		ReversedSeekBar bar = (ReversedSeekBar) findViewById(R.id.tabInfoSliderChargerControl);
		TextView tvl = (TextView) findViewById(R.id.tabInfoTextChargeStatusLeft);
		TextView tvr = (TextView) findViewById(R.id.tabInfoTextChargeStatusRight);
		if ((!pCarData.car_chargeport_open)
				|| (pCarData.car_charge_substate_i_raw == 0x07)) {
			// Charge port is closed or car is not plugged in
			findViewById(R.id.tabInfoImageCharger)
					.setVisibility(View.INVISIBLE);
			bar.setVisibility(View.INVISIBLE);
			cmtv.setVisibility(View.INVISIBLE);
			coiv.setVisibility(View.INVISIBLE);
			tvl.setVisibility(View.INVISIBLE);
			tvr.setVisibility(View.INVISIBLE);
		} else {
			// Car is plugged in
			findViewById(R.id.tabInfoImageCharger).setVisibility(View.VISIBLE);
			bar.setVisibility(View.VISIBLE);
			tvl.setVisibility(View.VISIBLE);
			tvr.setVisibility(View.VISIBLE);

			switch (pCarData.car_charge_state_i_raw) {
			case 0x04: // Done
			case 0x115: // Stopping
			case 0x15: // Stopped
			case 0x16: // Stopped
			case 0x17: // Stopped
			case 0x18: // Stopped
			case 0x19: // Stopped
				// Slider on the left, message is "Slide to charge"
				bar.setProgress(100);
				tvl.setText(null);
				tvr.setText(getText(R.string.slidetocharge));
				coiv.setVisibility(View.VISIBLE);
				cmtv.setVisibility(View.INVISIBLE);
				break;
			case 0x0e: // Wait for schedule charge
				// Slider on the left, message is "Timed Charge"
				bar.setProgress(100);
				tvl.setText(null);
				tvr.setText(getText(R.string.timedcharge));
				coiv.setVisibility(View.VISIBLE);
				cmtv.setVisibility(View.INVISIBLE);
				break;
			case 0x01: // Charging
			case 0x101: // Starting
			case 0x02: // Top-off
			case 0x0d: // Preparing to charge
			case 0x0f: // Heating
				// Slider on the right, message blank
				bar.setProgress(0);
				tvl.setText(String.format(
						getText(R.string.charging).toString(),
						pCarData.car_charge_linevoltage,
						pCarData.car_charge_current));
				tvr.setText("");
				cmtv.setText(String.format("%s %s", pCarData.car_charge_mode,
						pCarData.car_charge_currentlimit).toUpperCase());
				coiv.setVisibility(View.VISIBLE);
				cmtv.setVisibility(View.VISIBLE);
				break;
			default:
				// Slider on the right, message blank
				bar.setProgress(100);
				tvl.setText(null);
				tvr.setText(null);
				cmtv.setVisibility(View.INVISIBLE);
				coiv.setVisibility(View.INVISIBLE);
				break;
			}
		}

		tv = (TextView) findViewById(R.id.tabInfoTextIdealRange);
		tv.setText(pCarData.car_range_ideal);
		tv = (TextView) findViewById(R.id.tabInfoTextEstimatedRange);
		tv.setText(pCarData.car_range_estimated);

		int maxWeight = findViewById(R.id.tabInfoTextSOC).getLayoutParams().width;
		int realWeight = Math
				.round((maxWeight * pCarData.car_soc_raw / 100) * 1.1f);
		View v = findViewById(R.id.tabInfoImageBatteryOverlay);

		v.getLayoutParams().width = Math.min(maxWeight, realWeight);
		v.requestLayout();

		ImageView iv = (ImageView) findViewById(R.id.tabInfoImageCar);
		iv.setImageResource(Ui.getDrawableIdentifier(getActivity(),
				"signal_strength_" + pCarData.car_gsm_bars));

		iv = (ImageView) findViewById(R.id.tabInfoImageCar);
		iv.setImageResource(Ui.getDrawableIdentifier(getActivity(),
				pCarData.sel_vehicle_image));
	}

}
