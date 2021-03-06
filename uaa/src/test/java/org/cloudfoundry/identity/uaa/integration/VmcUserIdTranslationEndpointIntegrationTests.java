/*
 * Cloud Foundry 2012.02.03 Beta
 * Copyright (c) [2009-2012] VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product includes a number of subcomponents with
 * separate copyright notices and license terms. Your use of these
 * subcomponents is subject to the terms and conditions of the
 * subcomponent's license, as noted in the LICENSE file.
 */
package org.cloudfoundry.identity.uaa.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.cloudfoundry.identity.uaa.message.PasswordChangeRequest;
import org.cloudfoundry.identity.uaa.scim.ScimUser;
import org.cloudfoundry.identity.uaa.scim.ScimUser.Group;
import org.cloudfoundry.identity.uaa.test.TestAccountSetup;
import org.cloudfoundry.identity.uaa.test.UaaTestAccounts;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.Ignore;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.test.BeforeOAuth2Context;
import org.springframework.security.oauth2.client.test.OAuth2ContextConfiguration;
import org.springframework.security.oauth2.client.test.OAuth2ContextSetup;
import org.springframework.security.oauth2.common.util.RandomValueStringGenerator;
import org.springframework.web.client.RestOperations;

/**
 * Integration test to verify that the userid translation use cases are supported adequately for vmc.
 *
 * @author Luke Taylor
 */
@OAuth2ContextConfiguration(OAuth2ContextConfiguration.Implicit.class)
public class VmcUserIdTranslationEndpointIntegrationTests {

	private final String JOE = "joe" + new RandomValueStringGenerator().generate().toLowerCase();

	private final String userEndpoint = "/Users";

	private final String idsEndpoint = "/ids/Users";

	private ScimUser joe;

	@Rule
	public ServerRunning serverRunning = ServerRunning.isRunning();

	private UaaTestAccounts testAccounts = UaaTestAccounts.standard(serverRunning);

	@Rule
	public TestAccountSetup testAccountSetup = TestAccountSetup.standard(serverRunning, testAccounts);

	@Rule
	public OAuth2ContextSetup context = OAuth2ContextSetup.withTestAccounts(serverRunning, testAccounts);

	@BeforeOAuth2Context
	@OAuth2ContextConfiguration(OAuth2ContextConfiguration.ClientCredentials.class)
	public void setUpUserAccounts() {

		// If running against vcap we don't want to run these tests because they create new user accounts
		// Assume.assumeTrue(!testAccounts.isProfileActive("vcap"));

		RestOperations client = serverRunning.getRestTemplate();

		ScimUser user = new ScimUser();
		user.setUserName(JOE);
		user.setName(new ScimUser.Name("Joe", "User"));
		user.addEmail("joe@blah.com");
		user.setGroups(Arrays.asList(new Group(null, "uaa.user"), new Group(null, "orgs.foo")));

		ResponseEntity<ScimUser> newuser = client.postForEntity(serverRunning.getUrl(userEndpoint), user,
				ScimUser.class);

		joe = newuser.getBody();
		assertEquals(JOE, joe.getUserName());

		PasswordChangeRequest change = new PasswordChangeRequest();
		change.setPassword("password");

		HttpHeaders headers = new HttpHeaders();
		ResponseEntity<Void> result = client.exchange(serverRunning.getUrl(userEndpoint) + "/{id}/password",
				HttpMethod.PUT, new HttpEntity<PasswordChangeRequest>(change, headers), null, joe.getId());
		assertEquals(HttpStatus.OK, result.getStatusCode());

		// The implicit grant for vmc requires extra parameters in the authorization request
		context.setParameters(Collections.singletonMap("credentials",
				testAccounts.getJsonCredentials(joe.getUserName(), "password")));

	}

	@Test
	@Ignore // XXX Ignoring all tests: user id translation not supported in AOK
	public void findUsersWithExplicitFilterSucceeds() throws Exception {
		@SuppressWarnings("rawtypes")
		ResponseEntity<Map> response = serverRunning.getForObject(idsEndpoint + "?filter=userName eq '" + JOE + "'", Map.class);
		@SuppressWarnings("unchecked")
		Map<String, Object> results = response.getBody();
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(1, results.get("totalResults"));
	}

	@Test
	@Ignore
	public void findUsersExplicitEmailFails() throws Exception {
		@SuppressWarnings("rawtypes")
		ResponseEntity<Map> response = serverRunning.getForObject(idsEndpoint + "?filter=emails.value sw 'joe'", Map.class);
		@SuppressWarnings("unchecked")
		Map<String, Object> results = response.getBody();
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertNotNull("There should be an error", results.get("error"));
	}

	@Test
	@Ignore
	public void findUsersExplicitPresentFails() throws Exception {
		@SuppressWarnings("rawtypes")
		ResponseEntity<Map> response = serverRunning.getForObject(idsEndpoint + "?filter=pr userType", Map.class);
		@SuppressWarnings("unchecked")
		Map<String, Object> results = response.getBody();
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertNotNull("There should be an error", results.get("error"));
	}

	@Test
	@Ignore
	public void findUsersExplicitGroupFails() throws Exception {
		@SuppressWarnings("rawtypes")
		ResponseEntity<Map> response = serverRunning.getForObject(idsEndpoint + "?filter=groups.display co 'foo'", Map.class);
		@SuppressWarnings("unchecked")
		Map<String, Object> results = response.getBody();
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertNotNull("There should be an error", results.get("error"));
	}
}
