/*
 * Copyright (c) 2014 All Rights Reserved, nickdsantos.com
 */

package com.nickdsantos.onedrive4j;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Callable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nickdsantos.onedrive4j.Resource.SharedWith;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Nick DS (me@nickdsantos.com)
 *
 */
public class PhotoService {
	static Logger logger = LoggerFactory.getLogger(AlbumService.class.getName());
	
	public static final String API_HOST = "apis.live.net/v5.0";
	public static final String DEFAULT_SCHEME = "https";	
	public static final String ALBUM_URL_PATH = "/me/albums";
	private static final Photo[] NO_PHOTOS = new Photo[0];
	private static final ImageItem[] NO_IMAGE_ITEMS = new ImageItem[0];
	private final Callable<CloseableHttpClient> _httpClientBuilder;

	/**
	 * This class should only be instantiated from the OneDrive.getPhotoService() method.
	 */
	protected PhotoService(Callable<CloseableHttpClient> httpClientBuilder) {
		_httpClientBuilder = httpClientBuilder;
	}
	
	/**
	 * Gets an array of Photo objects.
	 * @param accessToken
	 * @param albumId
	 * @return
	 * @throws IOException
	 */
	public Photo[] getPhotos(String accessToken, String albumId) throws IOException {
		List<Photo> photos = new ArrayList<>();
		URI uri;
		try {			
			uri = new URIBuilder()
						.setScheme(DEFAULT_SCHEME)
						.setHost(API_HOST)
						.setPath("/" + albumId + "/files")
						.addParameter("access_token", accessToken)
						.build();
		} catch (URISyntaxException e) {
			throw new IllegalStateException("Invalid album path", e);
		}

		try (CloseableHttpClient httpClient = _httpClientBuilder.call()) {
			HttpGet httpGet = new HttpGet(uri);
			Map<Object, Object> rawResponse = httpClient.execute(httpGet, new OneDriveJsonToMapResponseHandler());
			if (rawResponse != null) {
				@SuppressWarnings("unchecked")
				List<Map<Object, Object>> rawResponseList = (List<Map<Object, Object>>) rawResponse.get("data");
				if (rawResponseList != null) {
					for (Map<Object, Object> respData : rawResponseList) {
						Photo p = createPhotoFromMap(respData);
						if (p != null) {
							photos.add(p);
						}
					}
				}
			}
		} catch (Exception e) {
			throw new IOException("Error getting photos, album: " + albumId, e);
		}
		
		return photos.toArray(NO_PHOTOS);
	}
	
	/**
	 * Gets a specific Photo
	 * @param accessToken
	 * @param photoId
	 * @return
	 * @throws IOException
	 */
	public Photo getPhoto(String accessToken, String photoId) throws IOException {
		Photo photo = null;
		URI uri;
		try {			
			uri = new URIBuilder()
						.setScheme(DEFAULT_SCHEME)
						.setHost(API_HOST)
						.setPath("/" + photoId)
						.addParameter("access_token", accessToken)
						.build();
		} catch (URISyntaxException e) {
			throw new IllegalStateException("Invalid album path", e);
		}

		try (CloseableHttpClient httpClient = _httpClientBuilder.call()) {
			HttpGet httpGet = new HttpGet(uri);
			Map<Object, Object> rawResponse = httpClient.execute(httpGet, new OneDriveJsonToMapResponseHandler());
			if (rawResponse != null) {
				photo = createPhotoFromMap(rawResponse);
			}
		} catch (Exception e) {
			throw new IOException("Error getting photo: " + photoId, e);
		}
		
		return photo;
	}
	
	/**
	 * Uploads an image to the given album. The returned Photo would only contain the new Photo Id
	 * and the automatically generated file name. To get the rest of the Photo's information,
	 * use the getPhoto() method.
	 * @param accessToken
	 * @param albumId
	 * @param format
	 * @param bytes
	 * @return
	 * @throws IOException
	 */
	public Photo uploadPhoto(String accessToken, String albumId, String format, byte[] bytes) throws IOException {
		Photo newPhoto = null;
		URI uri;
		try {			
			uri = new URIBuilder()
						.setScheme(DEFAULT_SCHEME)
						.setHost(API_HOST)
						.setPath("/" + albumId + "/files/" + UUID.randomUUID().toString() + "." + format)
						.addParameter("access_token", accessToken)
						.addParameter("downsize_photo_uploads", "false")
						.build();
		} catch (URISyntaxException e) {
			throw new IllegalStateException("Invalid album path", e);
		}

		try (CloseableHttpClient httpClient = _httpClientBuilder.call()) {
			HttpPut httpPut = new HttpPut(uri);
			ByteArrayEntity imageEntity = new ByteArrayEntity(bytes);
			httpPut.setEntity(imageEntity);

			Map<Object, Object> rawResponse = httpClient.execute(httpPut, new OneDriveJsonToMapResponseHandler());
			if (rawResponse != null) {
				//newPhoto = getPhoto(accessToken, rawResponse.get("id").toString());
				//newPhoto = createPhotoFromMap(rawResponse);
				// This will only return a "naked" photo object, with just the id and name populated
				newPhoto = new Photo();
				newPhoto.setName((String) rawResponse.get("name"));
				newPhoto.setId((String) rawResponse.get("id"));
			}
		} catch (Exception e) {
			throw new IOException("Error uploading photo", e);
		}				
		
		return newPhoto;
	}
	
	/**
	 * * Uploads an image to the given album. The returned Photo would only contain the new Photo Id
	 * and the automatically generated file name. To get the rest of the Photo's information,
	 * use the getPhoto() method.
	 * 
	 * This method also updates the photo's description.
	 * @param accessToken
	 * @param albumId
	 * @param format
	 * @param description
	 * @param bytes
	 * @return
	 * @throws IOException
	 */
	public Photo uploadPhoto(String accessToken, String albumId, String format, String description, byte[] bytes) throws IOException {
		Photo newPhoto = uploadPhoto(accessToken, albumId, format, bytes);		
		newPhoto = updatePhotoDescription(accessToken, newPhoto.getId(), description);
		
		return newPhoto;
	}
	
	/**
	 * Updates the Photo's description. Even if the OneDrive API supports updating the name, it's not supported in this client API library.
	 * Updating a Photo's name is actually updating its filename that could potentially null out other meta-data like camera's make and model.
	 * @param accessToken
	 * @param photoId
	 * @param description
	 * @return
	 * @throws IOException
	 */
	public Photo updatePhotoDescription(String accessToken, String photoId, String description) throws IOException {
		Photo updatedPhoto = null;
		URI uri;
		try {			
			uri = new URIBuilder()
						.setScheme(DEFAULT_SCHEME)
						.setHost(API_HOST)
						.setPath("/" + photoId)
						.build();
		} catch (URISyntaxException e) {
			throw new IllegalStateException("Invalid album path", e);
		}

		try (CloseableHttpClient httpClient = _httpClientBuilder.call()) {
			Map<String, String> params = new HashMap<>();
			params.put("description", description);

			final Gson gson = new GsonBuilder().create();
			String jsonString = gson.toJson(params);
			StringEntity jsonEntity = new StringEntity(jsonString);
			jsonEntity.setContentType(new BasicHeader("Content-Type", "application/json"));

			HttpPut httpPut = new HttpPut(uri);
			httpPut.setHeader("Authorization", "Bearer " + accessToken);
			httpPut.setEntity(jsonEntity);

			Map<Object, Object> rawResponse = httpClient.execute(httpPut, new OneDriveJsonToMapResponseHandler());
			if (rawResponse != null) {
				updatedPhoto = createPhotoFromMap(rawResponse);
				// Do not get the photo id. revert to the original prior to the update
				updatedPhoto.setId(photoId);
			}
		} catch (Exception e) {
			throw new IOException("Error uploading photo description", e);
		}
		
		return updatedPhoto;
	}		
	
	/**
	 * Deletes a specific Photo
	 * @param accessToken
	 * @param photoId
	 * @throws IOException
	 */
	public void deletePhoto(String accessToken, String photoId) throws IOException {
		URI uri;
		try {			
			uri = new URIBuilder()
						.setScheme(DEFAULT_SCHEME)
						.setHost(API_HOST)
						.setPath("/" + photoId)
						.addParameter("access_token", accessToken)
						.build();
		} catch (URISyntaxException e) {
			throw new IllegalStateException("Invalid photo path", e);
		}

		try (CloseableHttpClient httpClient = _httpClientBuilder.call()) {
			HttpDelete httpDelete = new HttpDelete(uri);
			Map<Object, Object> rawResponse = httpClient.execute(httpDelete, new OneDriveJsonToMapResponseHandler());
			if (rawResponse != null) {
				System.out.println(rawResponse);
			}
		} catch (Exception e) {
			throw new RuntimeException("Error creating HTTP Client", e);
		}
	}
	
	private Photo createPhotoFromMap(Map<Object, Object> responseMap) {
		if (logger.isDebugEnabled()) {
			for (Object k : responseMap.keySet()) {
				logger.debug(k + " : " + responseMap.get(k));
			}
		}
		
		SimpleDateFormat dtFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
		Photo photo = null;
		if (responseMap.get("type").equals("photo")) {
			try {
				// Get embedded from JSON
				Map<?, ?> fromUserMap = (Map<?, ?>) responseMap.get("from");
				User fromUser = new User();
				fromUser.setId(fromUserMap.get("id").toString());
				fromUser.setName(fromUserMap.get("name").toString());
				
				// Get embedded shared_with JSON
				Map<?, ?> sharedWithMap = (Map<?, ?>) responseMap.get("shared_with");
				SharedWith sharedWith = SharedWith.parse(sharedWithMap.get("access").toString());
				
				// Get embedded images JSON array
				List<?> imageMapList = (List<?>) responseMap.get("images");
				List<ImageItem> images = new ArrayList<>();
				for (Object imageMapObject : imageMapList) {
					Map<?, ?> imageMap = (Map<?, ?>) imageMapObject;
					ImageItem image = new ImageItem();
					image.setHeight((double) imageMap.get("height"));
					image.setWidth((double) imageMap.get("width"));
					image.setSource((String) imageMap.get("source"));
					image.setType((String) imageMap.get("type"));
					
					images.add(image);
				}
				
				// Get embedded location JSON
				Map<?, ?> locationMap = (Map<?, ?>) responseMap.get("location");
				Location location = null;
				if (locationMap != null) {
					location = new Location();
					location.setLongitude((double) locationMap.get("longitude"));
					location.setLatitude((double) locationMap.get("latitude"));
					location.setAltitude((double) locationMap.get("altitude"));
				}
				
				photo = new Photo();
				photo.setId((String) responseMap.get("id"));
				photo.setName((String) responseMap.get("name"));
				photo.setFrom(fromUser);
				photo.setDescription((String) responseMap.get("description"));
				photo.setParentId((String) responseMap.get("parent_id"));
				photo.setSize((double) responseMap.get("size"));
				photo.setCommentsCount(((Number) responseMap.get("comments_count")).intValue());
				photo.setCommentsEnabled((boolean) responseMap.get("comments_enabled"));
				photo.setTagsCount(((Number) responseMap.get("tags_count")).intValue());
				photo.setTagsEnabled((boolean) responseMap.get("tags_enabled"));
				photo.setIsEmbeddable((boolean) responseMap.get("is_embeddable"));
				photo.setLink((String) responseMap.get("picture"));
				photo.setLink((String) responseMap.get("source"));
				photo.setUploadLocation((String) responseMap.get("upload_location"));
				photo.setImages(images.toArray(NO_IMAGE_ITEMS));
				photo.setLink((String) responseMap.get("link"));	
				if (responseMap.get("when_taken") != null)
					photo.setWhenTaken(dtFormat.parse((String) responseMap.get("when_taken")));
				photo.setWidth((double) responseMap.get("width"));
				photo.setHeight((double) responseMap.get("height"));
				photo.setType((String) responseMap.get("type"));
				if (location != null)
					photo.setLocation(location);
				photo.setCameraMake((String) responseMap.get("camera_make"));
				photo.setCameraModel((String) responseMap.get("camera_model"));
				photo.setFocalLength((double) responseMap.get("focal_length"));
				photo.setFocalRatio((double) responseMap.get("focal_ratio"));
				photo.setExposureNumerator((double) responseMap.get("exposure_numerator"));
				photo.setExposureDenominator((double) responseMap.get("exposure_numerator"));
				photo.setSharedWith(sharedWith);
				photo.setCreatedTime(dtFormat.parse((String) responseMap.get("created_time")));
				photo.setUpdatedTime(dtFormat.parse((String) responseMap.get("updated_time")));
				photo.setClientUpdateTime(dtFormat.parse((String) responseMap.get("client_updated_time")));																
			} catch (Exception e) {
				throw new IllegalStateException("Error getting photo", e);
			}
		}
		
		return photo;
	}
}
