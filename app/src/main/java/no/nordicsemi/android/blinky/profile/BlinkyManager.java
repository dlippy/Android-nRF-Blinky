/*
 * Copyright (c) 2015, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package no.nordicsemi.android.blinky.profile;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.support.annotation.NonNull;

import java.util.Deque;
import java.util.LinkedList;
import java.util.UUID;

import no.nordicsemi.android.ble.BleManager;
import no.nordicsemi.android.ble.Request;
import no.nordicsemi.android.log.ILogSession;
import no.nordicsemi.android.log.LogContract;
import android.util.Log;

import static android.bluetooth.BluetoothGattCharacteristic.FORMAT_UINT16;
import static android.bluetooth.BluetoothGattCharacteristic.FORMAT_UINT32;

public class BlinkyManager extends BleManager<BlinkyManagerCallbacks> {
	/** Nordic Blinky Service UUID. */
	public final static UUID LBS_UUID_SERVICE = UUID.fromString("00001523-1212-efde-1523-785feabcd123");
	/** BUTTON characteristic UUID. */
	private final static UUID LBS_UUID_BUTTON_CHAR = UUID.fromString("00001524-1212-efde-1523-785feabcd123");
	/** LED characteristic UUID. */
	private final static UUID LBS_UUID_LED_CHAR = UUID.fromString("00001525-1212-efde-1523-785feabcd123");

	private final static UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

	//inside ride service uuid
	public final static UUID IR_UUID_SERVICE = UUID.fromString("98CD1800-9228-4D42-B679-9BADF04CC3F6");
	/** Weight characteristic UUID. */
	private final static UUID IR_UUID_WEIGHT_CHAR = UUID.fromString("98CD0006-9228-4D42-B679-9BADF04CC3F6");

	//inside ride service uuid
	public final static UUID FTMS_UUID_SERVICE = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb");
	/** Weight characteristic UUID. */
	private final static UUID FTMS_UUID_INDOOR_BIKE_DATA_CHAR = UUID.fromString("00002AD2-0000-1000-8000-00805f9b34fb");
	public ILogSession logSession;

	private BluetoothGattCharacteristic mButtonCharacteristic, mLedCharacteristic, mWeightCharacteristic, mBikeDataCharacteristic;

	public BlinkyManager(final Context context) {

		super(context);
		setLogger(logSession);
	}

	@NonNull
	@Override
	protected BleManagerGattCallback getGattCallback() {
		return mGattCallback;
	}

	@Override
	protected boolean shouldAutoConnect() {
		// If you want to connect to the device using autoConnect flag = true, return true here.
		// Read the documentation of this method.
		return super.shouldAutoConnect();
	}

	/**
	 * BluetoothGatt callbacks for connection/disconnection, service discovery, receiving indication, etc
	 */
	private final BleManagerGattCallback mGattCallback = new BleManagerGattCallback() {

		@Override
		protected Deque<Request> initGatt(final BluetoothGatt gatt) {
			final LinkedList<Request> requests = new LinkedList<>();
			requests.push(Request.newReadRequest(mLedCharacteristic));
			requests.push(Request.newReadRequest(mButtonCharacteristic));
			requests.push(Request.newEnableNotificationsRequest(mButtonCharacteristic));
			return requests;
		}

		@Override
		public boolean isRequiredServiceSupported(final BluetoothGatt gatt) {
			BluetoothGattService service = gatt.getService(IR_UUID_SERVICE);
			if (service != null) {
				mWeightCharacteristic = service.getCharacteristic(IR_UUID_WEIGHT_CHAR);
			}

			if (mWeightCharacteristic != null) {
				readCharacteristic(mWeightCharacteristic);
			}

			service = gatt.getService(FTMS_UUID_SERVICE);
			if (service != null) {
				mBikeDataCharacteristic = service.getCharacteristic(FTMS_UUID_INDOOR_BIKE_DATA_CHAR);
			}
			if (mBikeDataCharacteristic != null) {
				enableNotifications(mBikeDataCharacteristic);

			}

			return mWeightCharacteristic != null;
		}

		@Override
		protected void onDeviceDisconnected() {
			mButtonCharacteristic = null;
			mLedCharacteristic = null;
		}

		@Override
		protected void onCharacteristicRead(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
			final int data = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
			if (characteristic == mWeightCharacteristic) {
				Log.d("WEIGHT", Integer.toString(mWeightCharacteristic.getIntValue(FORMAT_UINT16, 0)));
			}
		}

		@Override
		public void onCharacteristicWrite(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
			// This method is only called for LED characteristic
			final int data = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
			final boolean ledOn = data == 0x01;
			log(LogContract.Log.Level.APPLICATION, "LED " + (ledOn ? "ON" : "OFF"));
			mCallbacks.onDataSent(ledOn);
		}

		@Override
		public void onCharacteristicNotified(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
			// This method is only called for Button characteristic
			if (characteristic == mBikeDataCharacteristic) {
				final double KPH_TO_MPH_CONVERSION_FACTOR = 0.621371;
				double instantaneous_speed = mBikeDataCharacteristic.getIntValue(FORMAT_UINT16, 2) / 100 * KPH_TO_MPH_CONVERSION_FACTOR;
				double instantaneous_cadence = mBikeDataCharacteristic.getIntValue(FORMAT_UINT16, 4) / 2;
				double total_distance = mBikeDataCharacteristic.getIntValue(FORMAT_UINT32, 6) & 0xFFFFFF;  //this is in meters need to convert to miles in app (note only 3 bytes are the distance so we get rid of last byte with & 0xffffff
				double resistance = mBikeDataCharacteristic.getIntValue(FORMAT_UINT16, 9);
				double power = mBikeDataCharacteristic.getIntValue(FORMAT_UINT16, 11);
				double time = mBikeDataCharacteristic.getIntValue(FORMAT_UINT16, 13);
				String logString = String.format("Speed: %.1f \n Cadence: %.1f \n Distance: %.1f\n Resistance: %.1f \n Power: %.1f \n Time: %.1f \n",
						instantaneous_speed, instantaneous_cadence, total_distance, resistance, power, time);
				Log.d("BIKEDATA", logString);
			}
		}
	};

	public void send(final boolean onOff) {
		// Are we connected?
		if (mLedCharacteristic == null)
			return;

		final byte[] command = new byte[] {(byte) (onOff ? 1 : 0)};
		log(LogContract.Log.Level.VERBOSE, "Turning LED " + (onOff ? "ON" : "OFF") + "...");
		writeCharacteristic(mLedCharacteristic, command);
	}
}
