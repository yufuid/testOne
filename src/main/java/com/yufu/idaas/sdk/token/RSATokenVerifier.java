package com.yufu.idaas.sdk.token;

import com.google.common.io.BaseEncoding;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.yufu.idaas.sdk.exception.*;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.util.Date;

/**
 * Created by shuowang on 2018/6/11.
 */
public class RSATokenVerifier implements ITokenVerifier {

    private RSAPublicKey publicKey;

    public RSATokenVerifier(String publicKeyInfo, boolean isFilePath) throws YufuInitException {

        try {
            String publicKeyString;
            if (isFilePath) {
                StringBuilder stringBuilder = new StringBuilder();
                String s;
                BufferedReader br = new BufferedReader(new FileReader(publicKeyInfo));
                while ((s = br.readLine()) != null) {
                    stringBuilder.append(s);
                }
                br.close();
                publicKeyString = stringBuilder.toString();
            } else {
                publicKeyString = publicKeyInfo;
            }
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            publicKeyString =
                publicKeyString.replaceAll("-----(.*)-----(\r\n?|\n|)([\\s\\S]*)(\r\n?|\n|)-----(.*)-----", "$3");
            publicKeyString = publicKeyString.replace("\n", "");
            publicKeyString = publicKeyString.replace(" ", "");
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(BaseEncoding.base64().decode(publicKeyString));
            this.publicKey = (RSAPublicKey) keyFactory.generatePublic(publicKeySpec);

        } catch (FileNotFoundException e) {
            throw new YufuInitException("Can not find public key file in given path " + e.getMessage());
        } catch (IOException e) {
            throw new YufuInitException("Public key file can not be read: " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            throw new YufuInitException("Cannot find algorithm " + e.getMessage());
        } catch (InvalidKeySpecException e) {
            throw new YufuInitException("Invalid Key " + e.getMessage());
        }

        this.publicKey = publicKey;
    }

    public JWT verify(final String token) throws VerifyException {
        if (token == null || "".equals(token)) {
            throw new InvalidTokenException("Token could not be empty");
        }

        SignedJWT jwt;
        try {
            jwt = SignedJWT.parse(token);

            // verify time
            Date now = new Date();
            if (!verifyExpiration(now, jwt.getJWTClaimsSet().getExpirationTime())) {
                throw new TokenExpiredException();
            }
            verifyNotBefore(now, jwt.getJWTClaimsSet().getNotBeforeTime());

            if (null == publicKey) {
                throw new CannotRetrieveKeyException();
            }
            // verify signature
            if (!verifySignature(jwt, publicKey)) {
                throw new InvalidSignatureException();
            }

            return convert(jwt);
        } catch (ParseException e) {
            throw new InvalidTokenException("Cannot parse token: " + e.getMessage());
        }
    }

    private boolean verifyExpiration(Date now, Date exp) {
        return exp != null && !exp.before(now);
    }

    private void verifyNotBefore(Date now, Date nbf) throws TokenTooEarlyException {
        if (nbf != null && nbf.after(now)) {
            throw new TokenTooEarlyException();
        }
    }

    private boolean verifySignature(SignedJWT jwt, RSAPublicKey publicKey) throws InvalidSignatureException {
        RSASSAVerifier verifier = new RSASSAVerifier(publicKey);
        try {
            return jwt.verify(verifier);
        } catch (JOSEException e) {
            throw new InvalidSignatureException();
        }
    }

    private static JWT convert(SignedJWT token) throws ParseException {
        JWTClaimsSet claims = token.getJWTClaimsSet();

        return JWT.builder()
            .keyId(token.getHeader().getKeyID())
            .audience(claims.getAudience().get(0))
            .expiration(claims.getExpirationTime())
            .issueAt(claims.getIssueTime())
            .issuer(claims.getIssuer())
            .jwtId(claims.getJWTID())
            .notBefore(claims.getNotBeforeTime())
            .subject(claims.getSubject())
            .claims(claims.getClaims())
            .build();
    }
}