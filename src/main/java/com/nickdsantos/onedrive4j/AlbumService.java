/*
 * Copyright (c) 2014 All Rights Reserved, nickdsantos.com
 */

package com.nickdsantos.onedrive4j;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nickdsantos.onedrive4j.Resource.SharedWith;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.log4j.Logger;

/**
 * @author Nick DS (me@nickdsantos.com)
 *
 */
public class AlbumService {
	static Logger logger = Logger.getLogger(AlbumService.class.getName());		
	
	public static final String API_HOST = "apis.live.net/v5.0";
	public static final String DEFAULT_SCHEME = "https";	
	public static final String ALBUM_URL_PATH = "/me/albums";
	private static final Album[] NO_ALBUMS = new Album[0];

	protected AlbumService() {}
	
	public Album[] getAlbums(String accessToken) throws IOException {
		List<Album> albums = new ArrayList<>();
		URI uri;
		try {			
			uri = new URIBuilder()
						.setScheme(DEFAULT_SCHEME)
						.setHost(API_HOST)
						.setPath(ALBUM_URL_PATH)
						.addParameter("access_token", accessToken)
						.build();
		} catch (URISyntaxException e) {
			throw new IllegalStateException("Invalid album path", e);
		}

		try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
			HttpGet httpGet = new HttpGet(uri);
			Map<Object, Object> rawResponse = httpClient.execute(httpGet, new OneDriveJsonToMapResponseHandler());
			if (rawResponse != null) {
				@SuppressWarnings("unchecked")
				List<Map<Object, Object>> rawResponseList = (List<Map<Object, Object>>) rawResponse.get("data");
				if (rawResponseList != null) {
					for (Map<Object, Object> respData : rawResponseList) {
						albums.add(createAlbumFromMap(respData));
					}
				}
			}
		} catch (Exception e) {
			throw new IOException("Error getting albums", e);
		}
		
		return albums.toArray(NO_ALBUMS);
	}

	public Album getAlbum(String accessToken, String albumId) throws IOException {
		Album album = null;
		URI uri;
		try {			
			uri = new URIBuilder()
						.setScheme(DEFAULT_SCHEME)
						.setHost(API_HOST)
						.setPath("/" + albumId)
						.addParameter("access_token", accessToken)
						.build();
		} catch (URISyntaxException e) {
			throw new IllegalStateException("Invalid album path", e);
		}

		try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
			HttpGet httpGet = new HttpGet(uri);
			Map<Object, Object> rawResponse = httpClient.execute(httpGet, new OneDriveJsonToMapResponseHandler());
			if (rawResponse != null) {
				album = createAlbumFromMap(rawResponse);
			}
		} catch (Exception e) {
			throw new IOException("Error getting album: " + albumId, e);
		}
		
		return album;
	}
	
	public Album createAlbum(String accessToken, String name, String description) throws IOException {
		Album newAlbum = null;
		URI uri;
		try {			
			uri = new URIBuilder()
						.setScheme(DEFAULT_SCHEME)
						.setHost(API_HOST)
						.setPath(ALBUM_URL_PATH)
						.build();
		} catch (URISyntaxException e) {
			throw new IllegalStateException("Invalid album path", e);
		}

		try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
			Map<Object, Object> rawResponse = executeRequest(httpClient, uri, name, description, accessToken);
			if (rawResponse != null) {
				if (rawResponse.containsKey("error")) {
					@SuppressWarnings("unchecked")
					Map<Object, Object> errorBody = (Map<Object, Object>) rawResponse.get("error");

					if (errorBody.get("code").equals("resource_already_exists")) {
						// album already exists; obtain that album and return it
						Album[] existingAlbums = this.getAlbums(accessToken);
						for (Album a : existingAlbums) {
							if (a.getName().equals(name)) {
								newAlbum = a;
								break;
							}
						}

					} else {
						throw new IOException(String.format("%s : %s", errorBody.get("code"), errorBody.get("message")));
					}
				} else {
					newAlbum = createAlbumFromMap(rawResponse);
				}
			} else {
				throw new IOException("No response");
			}

		}
	
		return newAlbum;
	}
	
	public void deleteAlbum(String accessToken, String albumId) throws IOException {
		URI uri;
		try {			
			uri = new URIBuilder()
						.setScheme(DEFAULT_SCHEME)
						.setHost(API_HOST)
						.setPath("/" + albumId)
						.addParameter("access_token", accessToken)
						.build();
		} catch (URISyntaxException e) {
			throw new IllegalStateException("Invalid album path", e);
		}

		try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
			HttpDelete httpDelete = new HttpDelete(uri);
			Map<Object, Object> rawResponse = httpClient.execute(httpDelete, new OneDriveJsonToMapResponseHandler());
			if (rawResponse != null) {
				System.out.println(rawResponse);
			}
		}
	}
	
	public Album updateAlbum(String accessToken, String albumId, String name, String description) throws IOException {
		Album updatedAlbum = null;
		URI uri;
		try {			
			uri = new URIBuilder()
				.setScheme(DEFAULT_SCHEME)
				.setHost(API_HOST)
				.setPath("/" + albumId)				
				.build();
		} catch (URISyntaxException e) {
			throw new IllegalStateException("Invalid album path", e);
		}

		try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
			Map<Object, Object> rawResponse = executeRequest(httpClient, uri, name, description, accessToken);
			if (rawResponse != null) {
				updatedAlbum = createAlbumFromMap(rawResponse);
				// Do not get the updated id. revert to the original prior to the update
				updatedAlbum.setId(albumId);
			}
		}
	
		return updatedAlbum;
	}

	private Album createAlbumFromMap(Map<Object, Object> responseMap) {
		SimpleDateFormat dtFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
		Album album;
		try {
			Map<?, ?> fromUserMap = (Map<?, ?>) responseMap.get("from");
			User fromUser = new User();
			fromUser.setId(fromUserMap.get("id").toString());
			fromUser.setName(fromUserMap.get("name").toString());

			Map<?, ?> sharedWithMap = (Map<?, ?>) responseMap.get("shared_with");
			SharedWith sharedWith = SharedWith.parse(sharedWithMap.get("access").toString());

			album = new Album();
			album.setId(responseMap.get("id").toString());
			album.setName(responseMap.get("name").toString());
			album.setFrom(fromUser);
			album.setDescription(responseMap.get("description").toString());
			album.setParentId(responseMap.get("parent_id").toString());
			album.setUploadLocation(responseMap.get("upload_location").toString());
			album.setLink(responseMap.get("link").toString());
			album.setType(responseMap.get("type").toString());
			album.setSharedWith(sharedWith);
			album.setCreatedTime(dtFormat.parse(responseMap.get("created_time").toString()));
			album.setUpdatedTime(dtFormat.parse(responseMap.get("updated_time").toString()));
			album.setClientUpdateTime(dtFormat.parse(responseMap.get("client_updated_time").toString()));
		} catch (Exception e) {
			throw new IllegalStateException("Error parsing album", e);
		}
		
		return album;
	}

	private Map<Object, Object> executeRequest(CloseableHttpClient httpClient, URI uri,
								String name, String description, String accessToken)
			throws IOException {

		Map<String, String> params = new HashMap<>();
		params.put("name", name);
		params.put("description", description);

		Gson gson = new GsonBuilder().create();
		String jsonString = gson.toJson(params);
		StringEntity jsonEntity = new StringEntity(jsonString);
		jsonEntity.setContentType(new BasicHeader("Content-Type", "application/json"));

		HttpPost httpPost = new HttpPost(uri);
		httpPost.setHeader("Authorization", "Bearer " + accessToken);
		httpPost.setEntity(jsonEntity);

		return httpClient.execute(httpPost, new OneDriveJsonToMapResponseHandler());
	}
}
