package com.chariotsolutions.nfc.plugin;

import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Util {

    static final String TAG = "NfcPlugin";

    static JSONObject ndefToJSON(Ndef ndef) {
        JSONObject json = new JSONObject();

        if (ndef != null) {
            try {

                Tag tag = ndef.getTag();
                // tag is going to be null for NDEF_FORMATABLE until NfcUtil.parseMessage is refactored
                if (tag != null) {
                    json.put("id", byteArrayToJSON(tag.getId()));
                    json.put("techTypes", new JSONArray(Arrays.asList(tag.getTechList())));
                }

                json.put("type", translateType(ndef.getType()));
                json.put("maxSize", ndef.getMaxSize());
                json.put("isWritable", ndef.isWritable());
                json.put("ndefMessage", messageToJSON(ndef.getCachedNdefMessage()));
                // Workaround for bug in ICS (Android 4.0 and 4.0.1) where
                // mTag.getTagService(); of the Ndef object sometimes returns null
                // see http://issues.mroland.at/index.php?do=details&task_id=47
                try {
                  json.put("canMakeReadOnly", ndef.canMakeReadOnly());
                } catch (NullPointerException e) {
                  json.put("canMakeReadOnly", JSONObject.NULL);
                }
            } catch (JSONException e) {
                Log.e(TAG, "Failed to convert ndef into json: " + ndef.toString(), e);
            }
        }
        return json;
    }

    static JSONObject tagToJSON(Tag tag) {
        JSONObject json = new JSONObject();

        if (tag != null) {
            try {
                json.put("id", byteArrayToJSON(tag.getId()));
                json.put("techTypes", new JSONArray(Arrays.asList(tag.getTechList())));
            } catch (JSONException e) {
                Log.e(TAG, "Failed to convert tag into json: " + tag.toString(), e);
            }
        }
        return json;
    }

    static String translateType(String type) {
        String translation;
        if (type.equals(Ndef.NFC_FORUM_TYPE_1)) {
            translation = "NFC Forum Type 1";
        } else if (type.equals(Ndef.NFC_FORUM_TYPE_2)) {
            translation = "NFC Forum Type 2";
        } else if (type.equals(Ndef.NFC_FORUM_TYPE_3)) {
            translation = "NFC Forum Type 3";
        } else if (type.equals(Ndef.NFC_FORUM_TYPE_4)) {
            translation = "NFC Forum Type 4";
        } else {
            translation = type;
        }
        return translation;
    }

    static NdefRecord[] jsonToNdefRecords(String ndefMessageAsJSON) throws JSONException {
        JSONArray jsonRecords = new JSONArray(ndefMessageAsJSON);
        NdefRecord[] records = new NdefRecord[jsonRecords.length()];
        for (int i = 0; i < jsonRecords.length(); i++) {
            JSONObject record = jsonRecords.getJSONObject(i);
            byte tnf = (byte) record.getInt("tnf");
            byte[] type = jsonToByteArray(record.getJSONArray("type"));
            byte[] id = jsonToByteArray(record.getJSONArray("id"));
            byte[] payload = jsonToByteArray(record.getJSONArray("payload"));
            records[i] = new NdefRecord(tnf, type, id, payload);
        }
        return records;
    }

    static JSONArray byteArrayToJSON(byte[] bytes) {
        JSONArray json = new JSONArray();
        for (byte aByte : bytes) {
            json.put(aByte);
        }
        return json;
    }

    static byte[] jsonToByteArray(JSONArray json) throws JSONException {
        byte[] b = new byte[json.length()];
        for (int i = 0; i < json.length(); i++) {
            b[i] = (byte) json.getInt(i);
        }
        return b;
    }

    static JSONArray messageToJSON(NdefMessage message) {
        if (message == null) {
            return null;
        }

        List<JSONObject> list = new ArrayList<JSONObject>();

        for (NdefRecord ndefRecord : message.getRecords()) {
            list.add(recordToJSON(ndefRecord));
        }

        return new JSONArray(list);
    }

    static JSONObject recordToJSON(NdefRecord record) {
        JSONObject json = new JSONObject();
        try {
            json.put("tnf", record.getTnf());
            json.put("type", byteArrayToJSON(record.getType()));
            json.put("id", byteArrayToJSON(record.getId()));
            json.put("payload", byteArrayToJSON(record.getPayload()));
        } catch (JSONException e) {
            //Not sure why this would happen, documentation is unclear.
            Log.e(TAG, "Failed to convert ndef record into json: " + record.toString(), e);
        }
        return json;
    }

    static String byteArrayToCUID(byte[] bytes, boolean isUltralight)
    {
        try
        {
            if (bytes.length == 4)
            {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                for(int i = bytes.length-1; i >= 0; --i) out.write(bytes[i]);
                String numeric = "" + Long.decode("0x" + getHex(out.toByteArray()));
                while(numeric.length() < 11) { numeric = "0"+numeric; }

                return addCheckDigit("0160" + numeric);
            }
            if (bytes.length == 7)
            {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                for(int i = 1; i <bytes.length; ++i) out.write(bytes[i]);
                String numeric = "" + Long.decode("0x" + getHex(out.toByteArray()));
                while(numeric.length() < 11) { numeric = "0"+numeric; }
                if(isUltralight)
                {
                    return addCheckDigit("0001" + numeric);
                }
                return addCheckDigit("0170" + numeric);
            }
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
        }
        return "";
    }

    public static String addCheckDigit(String s)
    {
        String digits = "";
        boolean shouldDouble = true;
        for(int i = s.length()-1; i >= 0; --i)
        {
            String c = s.substring(i,i+1);
            int value = Integer.parseInt(c);
            if(shouldDouble) value *= 2;
            shouldDouble = !shouldDouble;
            digits = digits + value;
        }
        int sum = 0;
        for(int i = 0; i < digits.length(); ++i)
        {
            String c = digits.substring(i,i+1);
            sum += Integer.parseInt(c);
        }
        sum %= 10;
        return s + ((10 - sum)%10);
    }

    static final String HEXES = "0123456789ABCDEF";
    public static String getHex( byte [] raw ) {
        if ( raw == null ) {
            return null;
        }
        final StringBuilder hex = new StringBuilder( 2 * raw.length );
        for ( final byte b : raw ) {
            hex.append(HEXES.charAt((b & 0xF0) >> 4))
               .append(HEXES.charAt((b & 0x0F)));
        }
        return hex.toString();
    }

}

