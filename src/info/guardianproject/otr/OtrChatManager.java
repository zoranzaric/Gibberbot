package info.guardianproject.otr;

//Originally: package com.zadov.beem;

import info.guardianproject.otr.app.im.service.ImConnectionAdapter;

import java.security.PublicKey;
import java.util.Hashtable;

import net.java.otr4j.OtrEngineImpl;
import net.java.otr4j.OtrEngineListener;
import net.java.otr4j.OtrException;
import net.java.otr4j.OtrPolicy;
import net.java.otr4j.OtrPolicyImpl;
import net.java.otr4j.session.SessionID;
import net.java.otr4j.session.SessionStatus;


import android.content.Context;
import android.util.Log;

/* OtrChatManager keeps track of the status of chats and their OTR stuff
 */
public class OtrChatManager implements OtrEngineListener {

	//the singleton instance
	private static OtrChatManager _instance;
	
	
	private final static String TAG = "OtrChatManager";
	
	private OtrEngineHostImpl mOtrEngineHost;
	private OtrEngineImpl mOtrEngine;	
	private Hashtable<String,SessionID> sessions;
	
	private OtrChatManager (int otrPolicy,Context context) throws Exception
	{
		mOtrEngineHost = new OtrEngineHostImpl(new OtrPolicyImpl(otrPolicy), context);
		
		mOtrEngine = new OtrEngineImpl(mOtrEngineHost);
		mOtrEngine.addOtrEngineListener(this);
		
		sessions = new Hashtable<String,SessionID>();
	}
	
	
	public static synchronized OtrChatManager getInstance(int otrPolicy, Context context) throws Exception
	{
		if (_instance == null)
		{
			_instance = new OtrChatManager(otrPolicy, context);
		}
		
		return _instance;
	}
	
	public void setConnection (ImConnectionAdapter imConnectionAdapter)
	{
		mOtrEngineHost.setConnection(imConnectionAdapter);
	}
	
	
	public void addOtrEngineListener (OtrEngineListener oel)
	{
		mOtrEngine.addOtrEngineListener(oel);
	}
	
	public OtrAndroidKeyManagerImpl getKeyManager ()
	{
		return mOtrEngineHost.getKeyManager();
	}
	
	public static String processUserId (String userId)
	{
		String result = userId.split(":")[0]; //remove any port indication in the username
		result = userId.split("/")[0];
		
		return result;
	}
	
	public SessionID getSessionId (String localUserId, String remoteUserId)
	{
		String sessionIdKey = processUserId(localUserId)+"+"+processUserId(remoteUserId);
		
		SessionID sessionId = sessions.get(sessionIdKey);
		
		if (sessionId == null)
		{
			sessionId = new SessionID(processUserId(localUserId), processUserId(remoteUserId), "XMPP");
			sessions.put(sessionIdKey, sessionId);
		}
		
		return sessionId;
	}
	
	/**
	 * Tell if the session represented by a local user account and a 
	 * remote user account is currently encrypted or not.
	 * @param localUserId
	 * @param remoteUserId
	 * @return state
	 */
	public SessionStatus getSessionStatus (String localUserId, String remoteUserId)
	{
		SessionID sessionId = getSessionId(localUserId,remoteUserId);
		
		return mOtrEngine.getSessionStatus(sessionId);
		
	}

	public SessionStatus getSessionStatus (SessionID sessionId)
	{
		
		return mOtrEngine.getSessionStatus(sessionId);
		
	}
	
	public void refreshSession (String localUserId, String remoteUserId)
	{
		try {
			mOtrEngine.refreshSession(getSessionId(localUserId,remoteUserId));
		} catch (OtrException e) {
			Log.e(TAG, "refreshSession", e);
		}
	}
	
	/**
	 * Start a new OTR encryption session for the chat session represented by a
	 * local user address and a remote user address. 
	 * @param localUserId i.e. the account of the user of this phone
	 * @param remoteUserId i.e. the account that this user is talking to
	 */
	public SessionID startSession(String localUserId, String remoteUserId) {
		
	
		try {
			SessionID sessionId = getSessionId(localUserId, remoteUserId);
			mOtrEngine.startSession(sessionId);
			
			return sessionId;
			
		} catch (OtrException e) {
			Log.e(TAG, "startSession", e);

		}
		
		return null;
	}
	
	public void endSession(String localUserId, String remoteUserId){
		
		try {
			SessionID sessionId = getSessionId(localUserId,remoteUserId);
		
			mOtrEngine.endSession(sessionId);
		} catch (OtrException e) {
			Log.e(TAG, "endSession", e);
		}
	}

	public void status(String localUserId, String remoteUserId){
		mOtrEngine.getSessionStatus(getSessionId(localUserId,remoteUserId)).toString();
	}
	
	public String decryptMessage(String localUserId, String remoteUserId, String msg){

		String plain = null;
		
		SessionID sessionId = getSessionId(localUserId,remoteUserId);
		Log.i(TAG,"session status: " + mOtrEngine.getSessionStatus(sessionId));

		if(mOtrEngine != null && sessionId != null){
			try {
				plain = mOtrEngine.transformReceiving(sessionId, msg);
			} catch (OtrException e) {
				// TODO Auto-generated catch block
				Log.e(TAG,"error decrypting message",e);
			}

		}
		return plain;
	}
	
	public void processMessageReceiving(String localUserId, String remoteUserId, String msg)
	{

		
		SessionID sessionId = getSessionId(localUserId,remoteUserId);
		Log.i(TAG,"session status: " + mOtrEngine.getSessionStatus(sessionId));

		if(mOtrEngine != null && sessionId != null){
			try {
				mOtrEngine.transformReceiving(sessionId, msg);
			} catch (OtrException e) {
				// TODO Auto-generated catch block
				Log.e(TAG,"error decrypting message",e);
			}

		}
	}
	
	public String encryptMessage(String localUserId, String remoteUserId, String msg){
		
		SessionID sessionId = getSessionId(localUserId,remoteUserId);

		Log.i(TAG,"session status: " + mOtrEngine.getSessionStatus(sessionId));

		if(mOtrEngine != null && sessionId != null) {
			try {
				msg = mOtrEngine.transformSending(sessionId, msg);
			} catch (OtrException e) {
				Log.d(TAG, "error encrypting", e);
			}	
		}
		return msg;
	}
	
	
	@Override
	public void sessionStatusChanged(SessionID sessionID) {
		SessionStatus sStatus = mOtrEngine.getSessionStatus(sessionID);
		
		Log.i(TAG,"session status changed: " + sStatus);
		
		if (sStatus == SessionStatus.ENCRYPTED)
		{
			
			PublicKey remoteKey = this.mOtrEngine.getRemotePublicKey(sessionID);
			mOtrEngineHost.storeRemoteKey(sessionID, remoteKey);
			
		}
		else if (sStatus == SessionStatus.PLAINTEXT)
		{
			
			
		}
		else if (sStatus == SessionStatus.FINISHED)
		{
			
		}
	}
	
	public String getLocalKeyFingerprint (String localUserId, String remoteUserId)
	{
		return mOtrEngineHost.getLocalKeyFingerprint(getSessionId(localUserId,remoteUserId));
	}
	
	public String getRemoteKeyFingerprint(String localUserId, String remoteUserId)
	{
		SessionID sessionID = getSessionId(localUserId,remoteUserId);
		String rkFingerprint = mOtrEngineHost.getRemoteKeyFingerprint(sessionID);

		if (rkFingerprint == null)
		{
			PublicKey remoteKey = mOtrEngine.getRemotePublicKey(sessionID);
			mOtrEngineHost.storeRemoteKey(sessionID, remoteKey);
			rkFingerprint = mOtrEngineHost.getRemoteKeyFingerprint(sessionID);
			Log.i(TAG,"remote key fingerprint: " + rkFingerprint);
		}
		return rkFingerprint;
	}

}
