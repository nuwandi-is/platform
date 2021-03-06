/*
*Copyright (c) 2005-2013, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*WSO2 Inc. licenses this file to you under the Apache License,
*Version 2.0 (the "License"); you may not use this file except
*in compliance with the License.
*You may obtain a copy of the License at
*
*http://www.apache.org/licenses/LICENSE-2.0
*
*Unless required by applicable law or agreed to in writing,
*software distributed under the License is distributed on an
*"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*KIND, either express or implied.  See the License for the
*specific language governing permissions and limitations
*under the License.
*/

package org.wso2.carbon.identity.oauth2.authz.handlers;

import org.apache.amber.oauth2.as.issuer.MD5Generator;
import org.apache.amber.oauth2.as.issuer.OAuthIssuerImpl;
import org.apache.amber.oauth2.common.message.types.ResponseType;
import org.apache.oltu.openidconnect.as.util.OIDCAuthzServerUtil;
import org.wso2.carbon.CarbonConstants;
import org.wso2.carbon.identity.oauth.cache.OAuthCache;
import org.wso2.carbon.identity.oauth.callback.OAuthCallback;
import org.wso2.carbon.identity.oauth.callback.OAuthCallbackManager;
import org.wso2.carbon.identity.oauth.config.OAuthServerConfiguration;
import org.wso2.carbon.identity.oauth.preprocessor.TokenPersistencePreprocessor;
import org.wso2.carbon.identity.oauth2.IdentityOAuth2Exception;
import org.wso2.carbon.identity.oauth2.authz.OAuthAuthzReqMessageContext;
import org.wso2.carbon.identity.oauth2.dao.TokenMgtDAO;
import org.wso2.carbon.identity.oauth2.dto.OAuth2AuthorizeReqDTO;
import org.wso2.carbon.identity.oauth2.dto.OAuth2AuthorizeRespDTO;
import org.wso2.carbon.identity.oauth2.util.OAuth2Util;
import org.wso2.carbon.identity.openidconnect.RememberMeStore;
import org.wso2.carbon.user.core.util.UserCoreUtil;

public abstract class AbstractAuthorizationHandler implements AuthorizationHandler {
    private OAuthCallbackManager callbackManager;
    protected OAuthIssuerImpl oauthIssuerImpl;
    protected TokenMgtDAO tokenMgtDAO;
    protected TokenPersistencePreprocessor tokenPersistencePreprocessor;
    protected boolean cacheEnabled;
    protected OAuthCache oauthCache;
    //protected OAuthCache<CacheKey, AccessTokenDO> oauthCache;

    public AbstractAuthorizationHandler()
            throws IdentityOAuth2Exception {
        callbackManager = new OAuthCallbackManager();
        oauthIssuerImpl = new OAuthIssuerImpl(new MD5Generator());
        tokenPersistencePreprocessor = OAuthServerConfiguration
                .getInstance().getTokenPersistencePreprocessor();
        tokenMgtDAO = new TokenMgtDAO();
        if(OAuthServerConfiguration.getInstance().isCacheEnabled()){
            cacheEnabled = true;
            oauthCache = OAuthCache.getInstance();
        }
    }

    public boolean authenticateResourceOwner(OAuthAuthzReqMessageContext oauthAuthzMsgCtx)
            throws IdentityOAuth2Exception {
		OAuth2AuthorizeReqDTO authorizationReqDTO = oauthAuthzMsgCtx.getAuthorizationReqDTO();
		boolean authStatus = false;
		// openid connect prompt= none support by remembering users
		if (OIDCAuthzServerUtil.isOIDCAuthzRequest(authorizationReqDTO.getScopes()) &&
		    authorizationReqDTO.getPassword() == null) {
			authStatus = RememberMeStore.getInstance().isUserInStore(authorizationReqDTO.getUsername());
		} else {
			authStatus =
			             OAuth2Util.authenticateUser(authorizationReqDTO.getUsername(),
			                                         authorizationReqDTO.getPassword());
		}
        if(authStatus){
        	// store the authenticated user for the openid-connect rememberMe usecase
			if (OIDCAuthzServerUtil.isOIDCAuthzRequest(authorizationReqDTO.getScopes())) {
				RememberMeStore.getInstance().addUserToStore(authorizationReqDTO.getUsername());
			}
			if (authorizationReqDTO.getUsername().indexOf(CarbonConstants.DOMAIN_SEPARATOR) < 0) {
				String userName = authorizationReqDTO.getUsername();
				if (UserCoreUtil.getDomainFromThreadLocal() != null) {
					userName =
					               UserCoreUtil.getDomainFromThreadLocal() +
					                       CarbonConstants.DOMAIN_SEPARATOR + userName;
				}
				authorizationReqDTO.setUsername(userName);
			} else if (authorizationReqDTO.getUsername().indexOf(CarbonConstants.DOMAIN_SEPARATOR) > 0) {
				authorizationReqDTO.setUsername(UserCoreUtil.getDomainFromThreadLocal() +
				                                CarbonConstants.DOMAIN_SEPARATOR +
				                                authorizationReqDTO.getUsername()
				                                                   .substring(authorizationReqDTO.getUsername()
				                                                                                 .indexOf(CarbonConstants.DOMAIN_SEPARATOR) + 1));
			}
        }
        return authStatus;
    }

    public boolean validateAccessDelegation(OAuthAuthzReqMessageContext oauthAuthzMsgCtx)
            throws IdentityOAuth2Exception {
        OAuth2AuthorizeReqDTO authorizationReqDTO = oauthAuthzMsgCtx.getAuthorizationReqDTO();
        OAuthCallback authzCallback = new OAuthCallback(
                authorizationReqDTO.getUsername(),
                authorizationReqDTO.getConsumerKey(),
                OAuthCallback.OAuthCallbackType.ACCESS_DELEGATION_AUTHZ);
        authzCallback.setRequestedScope(authorizationReqDTO.getScopes());
        authzCallback.setResponseType(ResponseType.valueOf(
                authorizationReqDTO.getResponseType().toUpperCase()));
        callbackManager.handleCallback(authzCallback);

        oauthAuthzMsgCtx.setValidityPeriod(authzCallback.getValidityPeriod());
        return authzCallback.isAuthorized();
    }


    public boolean validateScope(OAuthAuthzReqMessageContext oauthAuthzMsgCtx)
            throws IdentityOAuth2Exception {
        OAuth2AuthorizeReqDTO authorizationReqDTO = oauthAuthzMsgCtx.getAuthorizationReqDTO();
        OAuthCallback scopeValidationCallback = new OAuthCallback(
                authorizationReqDTO.getUsername(),
                authorizationReqDTO.getConsumerKey(),
                OAuthCallback.OAuthCallbackType.SCOPE_VALIDATION_AUTHZ);
        scopeValidationCallback.setRequestedScope(authorizationReqDTO.getScopes());
        scopeValidationCallback.setResponseType(ResponseType.valueOf(
                authorizationReqDTO.getResponseType().toUpperCase()));

        callbackManager.handleCallback(scopeValidationCallback);

        oauthAuthzMsgCtx.setValidityPeriod(scopeValidationCallback.getValidityPeriod());
        oauthAuthzMsgCtx.setApprovedScope(scopeValidationCallback.getApprovedScope());
        return scopeValidationCallback.isValidScope();
    }
}
