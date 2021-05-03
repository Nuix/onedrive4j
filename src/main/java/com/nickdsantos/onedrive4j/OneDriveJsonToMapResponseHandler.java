/*
 * Copyright (c) 2014 All Rights Reserved, nickdsantos.com
 */

package com.nickdsantos.onedrive4j;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;

/**
 * @author Nick DS (me@nickdsantos.com)
 */
public class OneDriveJsonToMapResponseHandler implements ResponseHandler<Map<Object,Object>> {

	private static final Type MAP_TYPE = new TypeToken<Map<Object, Object>>(){}.getType();

	/* (non-Javadoc)
	 * @see org.apache.http.client.ResponseHandler#handleResponse(org.apache.http.HttpResponse)
	 */
	@Override
	public Map<Object,Object> handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
		StatusLine statusLine = response.getStatusLine();		
		System.out.println("code: " + statusLine.getStatusCode() +"; reason: " + statusLine.getReasonPhrase());
		HttpEntity respEntity = response.getEntity();
		if (respEntity != null) {				
			Gson gson = new GsonBuilder().create();
			Reader reader = new InputStreamReader(respEntity.getContent(), StandardCharsets.UTF_8);
			return gson.fromJson(reader, MAP_TYPE);
		}
		
		return null;
	}
}
