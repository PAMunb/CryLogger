/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package javax.crypto;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.*;


import java.security.*;
import java.security.Provider.Service;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import javax.crypto.spec.*;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;

import sun.security.util.Debug;
import sun.security.jca.*;

/**
 * This class provides the functionality of a cryptographic cipher for
 * encryption and decryption. It forms the core of the Java Cryptographic
 * Extension (JCE) framework.
 *
 * <p>In order to create a Cipher object, the application calls the
 * Cipher's <code>getInstance</code> method, and passes the name of the
 * requested <i>transformation</i> to it. Optionally, the name of a provider
 * may be specified.
 *
 * <p>A <i>transformation</i> is a string that describes the operation (or
 * set of operations) to be performed on the given input, to produce some
 * output. A transformation always includes the name of a cryptographic
 * algorithm (e.g., <i>DES</i>), and may be followed by a feedback mode and
 * padding scheme.
 *
 * <p> A transformation is of the form:<p>
 *
 * <ul>
 * <li>"<i>algorithm/mode/padding</i>" or
 * <p>
 * <li>"<i>algorithm</i>"
 * </ul>
 *
 * <P> (in the latter case,
 * provider-specific default values for the mode and padding scheme are used).
 * For example, the following is a valid transformation:<p>
 *
 * <pre>
 *     Cipher c = Cipher.getInstance("<i>DES/CBC/PKCS5Padding</i>");
 * </pre>
 *
 * Using modes such as <code>CFB</code> and <code>OFB</code>, block
 * ciphers can encrypt data in units smaller than the cipher's actual
 * block size.  When requesting such a mode, you may optionally specify
 * the number of bits to be processed at a time by appending this number
 * to the mode name as shown in the "<code>DES/CFB8/NoPadding</code>" and
 * "<code>DES/OFB32/PKCS5Padding</code>" transformations. If no such
 * number is specified, a provider-specific default is used. (For
 * example, the SunJCE provider uses a default of 64 bits for DES.)
 * Thus, block ciphers can be turned into byte-oriented stream ciphers by
 * using an 8 bit mode such as CFB8 or OFB8.
 * <p>
 * Modes such as Authenticated Encryption with Associated Data (AEAD)
 * provide authenticity assurances for both confidential data and
 * Additional Associated Data (AAD) that is not encrypted.  (Please see
 * <a href="http://www.ietf.org/rfc/rfc5116.txt"> RFC 5116 </a> for more
 * information on AEAD and AEAD algorithms such as GCM/CCM.) Both
 * confidential and AAD data can be used when calculating the
 * authentication tag (similar to a {@link Mac}).  This tag is appended
 * to the ciphertext during encryption, and is verified on decryption.
 * <p>
 * AEAD modes such as GCM/CCM perform all AAD authenticity calculations
 * before starting the ciphertext authenticity calculations.  To avoid
 * implementations having to internally buffer ciphertext, all AAD data
 * must be supplied to GCM/CCM implementations (via the {@code
 * updateAAD} methods) <b>before</b> the ciphertext is processed (via
 * the {@code update} and {@code doFinal} methods).
 *
 * <pre>
 *     GCMParameterSpec s = new GCMParameterSpec(...);
 *     cipher.init(..., s);
 *
 *     // If the GCMParameterSpec is needed again
 *     cipher.getParameters().getParameterSpec(GCMParameterSpec.class));
 *
 *     cipher.updateAAD(...);  // AAD
 *     cipher.update(...);     // Multi-part update
 *     cipher.doFinal(...);    // conclusion of operation
 * </pre>
 * Every implementation of the Java platform is required to support
 * the following standard <code>Cipher</code> transformations with the keysizes
 * in parentheses:
 * <ul>
 * <li><tt>AES/CBC/NoPadding</tt> (128)</li>
 * <li><tt>AES/CBC/PKCS5Padding</tt> (128)</li>
 * <li><tt>AES/ECB/NoPadding</tt> (128)</li>
 * <li><tt>AES/ECB/PKCS5Padding</tt> (128)</li>
 * <li><tt>DES/CBC/NoPadding</tt> (56)</li>
 * <li><tt>DES/CBC/PKCS5Padding</tt> (56)</li>
 * <li><tt>DES/ECB/NoPadding</tt> (56)</li>
 * <li><tt>DES/ECB/PKCS5Padding</tt> (56)</li>
 * <li><tt>DESede/CBC/NoPadding</tt> (168)</li>
 * <li><tt>DESede/CBC/PKCS5Padding</tt> (168)</li>
 * <li><tt>DESede/ECB/NoPadding</tt> (168)</li>
 * <li><tt>DESede/ECB/PKCS5Padding</tt> (168)</li>
 * <li><tt>RSA/ECB/PKCS1Padding</tt> (1024, 2048)</li>
 * <li><tt>RSA/ECB/OAEPWithSHA-1AndMGF1Padding</tt> (1024, 2048)</li>
 * <li><tt>RSA/ECB/OAEPWithSHA-256AndMGF1Padding</tt> (1024, 2048)</li>
 * </ul>
 * These transformations are described in the
 * <a href="{@docRoot}/../technotes/guides/security/StandardNames.html#Cipher">
 * Cipher section</a> of the
 * Java Cryptography Architecture Standard Algorithm Name Documentation.
 * Consult the release documentation for your implementation to see if any
 * other transformations are supported.
 *
 * @author Jan Luehe
 * @see KeyGenerator
 * @see SecretKey
 * @since 1.4
 */

public class Cipher {

    private static final Debug debug =
                        Debug.getInstance("jca", "Cipher");

    /**
     * Constant used to initialize cipher to encryption mode.
     */
    public static final int ENCRYPT_MODE = 1;

    /**
     * Constant used to initialize cipher to decryption mode.
     */
    public static final int DECRYPT_MODE = 2;

    /**
     * Constant used to initialize cipher to key-wrapping mode.
     */
    public static final int WRAP_MODE = 3;

    /**
     * Constant used to initialize cipher to key-unwrapping mode.
     */
    public static final int UNWRAP_MODE = 4;

    /**
     * Constant used to indicate the to-be-unwrapped key is a "public key".
     */
    public static final int PUBLIC_KEY = 1;

    /**
     * Constant used to indicate the to-be-unwrapped key is a "private key".
     */
    public static final int PRIVATE_KEY = 2;

    /**
     * Constant used to indicate the to-be-unwrapped key is a "secret key".
     */
    public static final int SECRET_KEY = 3;

    // The provider
    private Provider provider;

    // The provider implementation (delegate)
    private CipherSpi spi;

    // The transformation
    private String transformation;

    // Crypto permission representing the maximum allowable cryptographic
    // strength that this Cipher object can be used for. (The cryptographic
    // strength is a function of the keysize and algorithm parameters encoded
    // in the crypto permission.)
    private CryptoPermission cryptoPerm;

    // The exemption mechanism that needs to be enforced
    private ExemptionMechanism exmech;

    // Flag which indicates whether or not this cipher has been initialized
    private boolean initialized = false;

    // The operation mode - store the operation mode after the
    // cipher has been initialized.
    private int opmode = 0;

    // The OID for the KeyUsage extension in an X.509 v3 certificate
    private static final String KEY_USAGE_EXTENSION_OID = "2.5.29.15";

    // next SPI  to try in provider selection
    // null once provider is selected
    private CipherSpi firstSpi;

    // next service to try in provider selection
    // null once provider is selected
    private Service firstService;

    // remaining services to try in provider selection
    // null once provider is selected
    private Iterator<Service> serviceIterator;

    // list of transform Strings to lookup in the provider
    private List<Transform> transforms;

    private final Object lock;

    /**
     * Creates a Cipher object.
     *
     * @param cipherSpi the delegate
     * @param provider the provider
     * @param transformation the transformation
     */
    protected Cipher(CipherSpi cipherSpi,
                     Provider provider,
                     String transformation) {
        // See bug 4341369 & 4334690 for more info.
        // If the caller is trusted, then okey.
        // Otherwise throw a NullPointerException.
        if (!JceSecurityManager.INSTANCE.isCallerTrusted()) {
            throw new NullPointerException();
        }
        this.spi = cipherSpi;
        this.provider = provider;
        this.transformation = transformation;
        this.cryptoPerm = CryptoAllPermission.INSTANCE;
        this.lock = null;
    }

    /**
     * Creates a Cipher object. Called internally and by NullCipher.
     *
     * @param cipherSpi the delegate
     * @param transformation the transformation
     */
    Cipher(CipherSpi cipherSpi, String transformation) {
        this.spi = cipherSpi;
        this.transformation = transformation;
        this.cryptoPerm = CryptoAllPermission.INSTANCE;
        this.lock = null;
    }

    private Cipher(CipherSpi firstSpi, Service firstService,
            Iterator<Service> serviceIterator, String transformation,
            List<Transform> transforms) {
        this.firstSpi = firstSpi;
        this.firstService = firstService;
        this.serviceIterator = serviceIterator;
        this.transforms = transforms;
        this.transformation = transformation;
        this.lock = new Object();
    }

    private static String[] tokenizeTransformation(String transformation)
            throws NoSuchAlgorithmException {
        if (transformation == null) {
            throw new NoSuchAlgorithmException("No transformation given");
        }
        /*
         * array containing the components of a Cipher transformation:
         *
         * index 0: algorithm component (e.g., DES)
         * index 1: feedback component (e.g., CFB)
         * index 2: padding component (e.g., PKCS5Padding)
         */
        String[] parts = new String[3];
        int count = 0;
        StringTokenizer parser = new StringTokenizer(transformation, "/");
        try {
            while (parser.hasMoreTokens() && count < 3) {
                parts[count++] = parser.nextToken().trim();
            }
            if (count == 0 || count == 2 || parser.hasMoreTokens()) {
                throw new NoSuchAlgorithmException("Invalid transformation"
                                               + " format:" +
                                               transformation);
            }
        } catch (NoSuchElementException e) {
            throw new NoSuchAlgorithmException("Invalid transformation " +
                                           "format:" + transformation);
        }
        if ((parts[0] == null) || (parts[0].length() == 0)) {
            throw new NoSuchAlgorithmException("Invalid transformation:" +
                                   "algorithm not specified-"
                                   + transformation);
        }
        return parts;
    }

    // Provider attribute name for supported chaining mode
    private final static String ATTR_MODE = "SupportedModes";
    // Provider attribute name for supported padding names
    private final static String ATTR_PAD  = "SupportedPaddings";

    // constants indicating whether the provider supports
    // a given mode or padding
    private final static int S_NO    = 0;       // does not support
    private final static int S_MAYBE = 1;       // unable to determine
    private final static int S_YES   = 2;       // does support

    /**
     * Nested class to deal with modes and paddings.
     */
    private static class Transform {
        // transform string to lookup in the provider
        final String transform;
        // the mode/padding suffix in upper case. for example, if the algorithm
        // to lookup is "DES/CBC/PKCS5Padding" suffix is "/CBC/PKCS5PADDING"
        // if loopup is "DES", suffix is the empty string
        // needed because aliases prevent straight transform.equals()
        final String suffix;
        // value to pass to setMode() or null if no such call required
        final String mode;
        // value to pass to setPadding() or null if no such call required
        final String pad;
        Transform(String alg, String suffix, String mode, String pad) {
            this.transform = alg + suffix;
            this.suffix = suffix.toUpperCase(Locale.ENGLISH);
            this.mode = mode;
            this.pad = pad;
        }
        // set mode and padding for the given SPI
        void setModePadding(CipherSpi spi) throws NoSuchAlgorithmException,
                NoSuchPaddingException {
            if (mode != null) {
                spi.engineSetMode(mode);
            }
            if (pad != null) {
                spi.engineSetPadding(pad);
            }
        }
        // check whether the given services supports the mode and
        // padding described by this Transform
        int supportsModePadding(Service s) {
            int smode = supportsMode(s);
            if (smode == S_NO) {
                return smode;
            }
            int spad = supportsPadding(s);
            // our constants are defined so that Math.min() is a tri-valued AND
            return Math.min(smode, spad);
        }

        // separate methods for mode and padding
        // called directly by Cipher only to throw the correct exception
        int supportsMode(Service s) {
            return supports(s, ATTR_MODE, mode);
        }
        int supportsPadding(Service s) {
            return supports(s, ATTR_PAD, pad);
        }

        private static int supports(Service s, String attrName, String value) {
            if (value == null) {
                return S_YES;
            }
            String regexp = s.getAttribute(attrName);
            if (regexp == null) {
                return S_MAYBE;
            }
            return matches(regexp, value) ? S_YES : S_NO;
        }

        // ConcurrentMap<String,Pattern> for previously compiled patterns
        private final static ConcurrentMap<String, Pattern> patternCache =
            new ConcurrentHashMap<String, Pattern>();

        private static boolean matches(String regexp, String str) {
            Pattern pattern = patternCache.get(regexp);
            if (pattern == null) {
                pattern = Pattern.compile(regexp);
                patternCache.putIfAbsent(regexp, pattern);
            }
            return pattern.matcher(str.toUpperCase(Locale.ENGLISH)).matches();
        }

    }

    private static List<Transform> getTransforms(String transformation)
            throws NoSuchAlgorithmException {
        String[] parts = tokenizeTransformation(transformation);

        String alg = parts[0];
        String mode = parts[1];
        String pad = parts[2];
        if ((mode != null) && (mode.length() == 0)) {
            mode = null;
        }
        if ((pad != null) && (pad.length() == 0)) {
            pad = null;
        }

        if ((mode == null) && (pad == null)) {
            // DES
            Transform tr = new Transform(alg, "", null, null);
            return Collections.singletonList(tr);
        } else { // if ((mode != null) && (pad != null)) {
            // DES/CBC/PKCS5Padding
            List<Transform> list = new ArrayList<>(4);
            list.add(new Transform(alg, "/" + mode + "/" + pad, null, null));
            list.add(new Transform(alg, "/" + mode, null, pad));
            list.add(new Transform(alg, "//" + pad, mode, null));
            list.add(new Transform(alg, "", mode, pad));
            return list;
        }
    }

    // get the transform matching the specified service
    private static Transform getTransform(Service s,
                                          List<Transform> transforms) {
        String alg = s.getAlgorithm().toUpperCase(Locale.ENGLISH);
        for (Transform tr : transforms) {
            if (alg.endsWith(tr.suffix)) {
                return tr;
            }
        }
        return null;
    }

    /**
     * Returns a <code>Cipher</code> object that implements the specified
     * transformation.
     *
     * <p> This method traverses the list of registered security Providers,
     * starting with the most preferred Provider.
     * A new Cipher object encapsulating the
     * CipherSpi implementation from the first
     * Provider that supports the specified algorithm is returned.
     *
     * <p> Note that the list of registered providers may be retrieved via
     * the {@link Security#getProviders() Security.getProviders()} method.
     *
     * @param transformation the name of the transformation, e.g.,
     * <i>DES/CBC/PKCS5Padding</i>.
     * See the Cipher section in the <a href=
     *   "{@docRoot}/../technotes/guides/security/StandardNames.html#Cipher">
     * Java Cryptography Architecture Standard Algorithm Name Documentation</a>
     * for information about standard transformation names.
     *
     * @return a cipher that implements the requested transformation.
     *
     * @exception NoSuchAlgorithmException if <code>transformation</code>
     *          is null, empty, in an invalid format,
     *          or if no Provider supports a CipherSpi implementation for the
     *          specified algorithm.
     *
     * @exception NoSuchPaddingException if <code>transformation</code>
     *          contains a padding scheme that is not available.
     *
     * @see java.security.Provider
     */
    public static final Cipher getInstance(String transformation)
            throws NoSuchAlgorithmException, NoSuchPaddingException
    {
        List<Transform> transforms = getTransforms(transformation);
        List<ServiceId> cipherServices = new ArrayList<>(transforms.size());
        for (Transform transform : transforms) {
            cipherServices.add(new ServiceId("Cipher", transform.transform));
        }
        List<Service> services = GetInstance.getServices(cipherServices);
        // make sure there is at least one service from a signed provider
        // and that it can use the specified mode and padding
        Iterator<Service> t = services.iterator();
        Exception failure = null;
        while (t.hasNext()) {
            Service s = t.next();
            if (JceSecurity.canUseProvider(s.getProvider()) == false) {
                continue;
            }
            Transform tr = getTransform(s, transforms);
            if (tr == null) {
                // should never happen
                continue;
            }
            int canuse = tr.supportsModePadding(s);
            if (canuse == S_NO) {
                // does not support mode or padding we need, ignore
                continue;
            }
            if (canuse == S_YES) {
                return new Cipher(null, s, t, transformation, transforms);
            } else { // S_MAYBE, try out if it works
                try {
                    CipherSpi spi = (CipherSpi)s.newInstance(null);
                    tr.setModePadding(spi);
                    return new Cipher(spi, s, t, transformation, transforms);
                } catch (Exception e) {
                    failure = e;
                }
            }
        }
        throw new NoSuchAlgorithmException
            ("Cannot find any provider supporting " + transformation, failure);
    }

    /**
     * Returns a <code>Cipher</code> object that implements the specified
     * transformation.
     *
     * <p> A new Cipher object encapsulating the
     * CipherSpi implementation from the specified provider
     * is returned.  The specified provider must be registered
     * in the security provider list.
     *
     * <p> Note that the list of registered providers may be retrieved via
     * the {@link Security#getProviders() Security.getProviders()} method.
     *
     * @param transformation the name of the transformation,
     * e.g., <i>DES/CBC/PKCS5Padding</i>.
     * See the Cipher section in the <a href=
     *   "{@docRoot}/../technotes/guides/security/StandardNames.html#Cipher">
     * Java Cryptography Architecture Standard Algorithm Name Documentation</a>
     * for information about standard transformation names.
     *
     * @param provider the name of the provider.
     *
     * @return a cipher that implements the requested transformation.
     *
     * @exception NoSuchAlgorithmException if <code>transformation</code>
     *          is null, empty, in an invalid format,
     *          or if a CipherSpi implementation for the specified algorithm
     *          is not available from the specified provider.
     *
     * @exception NoSuchProviderException if the specified provider is not
     *          registered in the security provider list.
     *
     * @exception NoSuchPaddingException if <code>transformation</code>
     *          contains a padding scheme that is not available.
     *
     * @exception IllegalArgumentException if the <code>provider</code>
     *          is null or empty.
     *
     * @see java.security.Provider
     */
    public static final Cipher getInstance(String transformation,
                                           String provider)
            throws NoSuchAlgorithmException, NoSuchProviderException,
            NoSuchPaddingException
    {
        if ((provider == null) || (provider.length() == 0)) {
            throw new IllegalArgumentException("Missing provider");
        }
        Provider p = Security.getProvider(provider);
        if (p == null) {
            throw new NoSuchProviderException("No such provider: " +
                                              provider);
        }
        return getInstance(transformation, p);
    }

    /**
     * Returns a <code>Cipher</code> object that implements the specified
     * transformation.
     *
     * <p> A new Cipher object encapsulating the
     * CipherSpi implementation from the specified Provider
     * object is returned.  Note that the specified Provider object
     * does not have to be registered in the provider list.
     *
     * @param transformation the name of the transformation,
     * e.g., <i>DES/CBC/PKCS5Padding</i>.
     * See the Cipher section in the <a href=
     *   "{@docRoot}/../technotes/guides/security/StandardNames.html#Cipher">
     * Java Cryptography Architecture Standard Algorithm Name Documentation</a>
     * for information about standard transformation names.
     *
     * @param provider the provider.
     *
     * @return a cipher that implements the requested transformation.
     *
     * @exception NoSuchAlgorithmException if <code>transformation</code>
     *          is null, empty, in an invalid format,
     *          or if a CipherSpi implementation for the specified algorithm
     *          is not available from the specified Provider object.
     *
     * @exception NoSuchPaddingException if <code>transformation</code>
     *          contains a padding scheme that is not available.
     *
     * @exception IllegalArgumentException if the <code>provider</code>
     *          is null.
     *
     * @see java.security.Provider
     */
    public static final Cipher getInstance(String transformation,
                                           Provider provider)
            throws NoSuchAlgorithmException, NoSuchPaddingException
    {
        if (provider == null) {
            throw new IllegalArgumentException("Missing provider");
        }
        Exception failure = null;
        List<Transform> transforms = getTransforms(transformation);
        boolean providerChecked = false;
        String paddingError = null;
        for (Transform tr : transforms) {
            Service s = provider.getService("Cipher", tr.transform);
            if (s == null) {
                continue;
            }
            if (providerChecked == false) {
                // for compatibility, first do the lookup and then verify
                // the provider. this makes the difference between a NSAE
                // and a SecurityException if the
                // provider does not support the algorithm.
                Exception ve = JceSecurity.getVerificationResult(provider);
                if (ve != null) {
                    String msg = "JCE cannot authenticate the provider "
                        + provider.getName();
                    throw new SecurityException(msg, ve);
                }
                providerChecked = true;
            }
            if (tr.supportsMode(s) == S_NO) {
                continue;
            }
            if (tr.supportsPadding(s) == S_NO) {
                paddingError = tr.pad;
                continue;
            }
            try {
                CipherSpi spi = (CipherSpi)s.newInstance(null);
                tr.setModePadding(spi);
                Cipher cipher = new Cipher(spi, transformation);
                cipher.provider = s.getProvider();
                cipher.initCryptoPermission();
                return cipher;
            } catch (Exception e) {
                failure = e;
            }
        }

        // throw NoSuchPaddingException if the problem is with padding
        if (failure instanceof NoSuchPaddingException) {
            throw (NoSuchPaddingException)failure;
        }
        if (paddingError != null) {
            throw new NoSuchPaddingException
                ("Padding not supported: " + paddingError);
        }
        throw new NoSuchAlgorithmException
                ("No such algorithm: " + transformation, failure);
    }

    // If the requested crypto service is export-controlled,
    // determine the maximum allowable keysize.
    private void initCryptoPermission() throws NoSuchAlgorithmException {
        if (JceSecurity.isRestricted() == false) {
            cryptoPerm = CryptoAllPermission.INSTANCE;
            exmech = null;
            return;
        }
        cryptoPerm = getConfiguredPermission(transformation);
        // Instantiate the exemption mechanism (if required)
        String exmechName = cryptoPerm.getExemptionMechanism();
        if (exmechName != null) {
            exmech = ExemptionMechanism.getInstance(exmechName);
        }
    }

    // max number of debug warnings to print from chooseFirstProvider()
    private static int warnCount = 10;

    /**
     * Choose the Spi from the first provider available. Used if
     * delayed provider selection is not possible because init()
     * is not the first method called.
     */
    void chooseFirstProvider() {
        if (spi != null) {
            return;
        }
        synchronized (lock) {
            if (spi != null) {
                return;
            }
            if (debug != null) {
                int w = --warnCount;
                if (w >= 0) {
                    debug.println("Cipher.init() not first method "
                        + "called, disabling delayed provider selection");
                    if (w == 0) {
                        debug.println("Further warnings of this type will "
                            + "be suppressed");
                    }
                    new Exception("Call trace").printStackTrace();
                }
            }
            Exception lastException = null;
            while ((firstService != null) || serviceIterator.hasNext()) {
                Service s;
                CipherSpi thisSpi;
                if (firstService != null) {
                    s = firstService;
                    thisSpi = firstSpi;
                    firstService = null;
                    firstSpi = null;
                } else {
                    s = serviceIterator.next();
                    thisSpi = null;
                }
                if (JceSecurity.canUseProvider(s.getProvider()) == false) {
                    continue;
                }
                Transform tr = getTransform(s, transforms);
                if (tr == null) {
                    // should never happen
                    continue;
                }
                if (tr.supportsModePadding(s) == S_NO) {
                    continue;
                }
                try {
                    if (thisSpi == null) {
                        Object obj = s.newInstance(null);
                        if (obj instanceof CipherSpi == false) {
                            continue;
                        }
                        thisSpi = (CipherSpi)obj;
                    }
                    tr.setModePadding(thisSpi);
                    initCryptoPermission();
                    spi = thisSpi;
                    provider = s.getProvider();
                    // not needed any more
                    firstService = null;
                    serviceIterator = null;
                    transforms = null;
                    return;
                } catch (Exception e) {
                    lastException = e;
                }
            }
            ProviderException e = new ProviderException
                    ("Could not construct CipherSpi instance");
            if (lastException != null) {
                e.initCause(lastException);
            }
            throw e;
        }
    }

    private final static int I_KEY       = 1;
    private final static int I_PARAMSPEC = 2;
    private final static int I_PARAMS    = 3;
    private final static int I_CERT      = 4;

    private void implInit(CipherSpi thisSpi, int type, int opmode, Key key,
            AlgorithmParameterSpec paramSpec, AlgorithmParameters params,
            SecureRandom random) throws InvalidKeyException,
            InvalidAlgorithmParameterException {
        switch (type) {
        case I_KEY:
            checkCryptoPerm(thisSpi, key);
            thisSpi.engineInit(opmode, key, random);
            break;
        case I_PARAMSPEC:
            checkCryptoPerm(thisSpi, key, paramSpec);
            thisSpi.engineInit(opmode, key, paramSpec, random);
            break;
        case I_PARAMS:
            checkCryptoPerm(thisSpi, key, params);
            thisSpi.engineInit(opmode, key, params, random);
            break;
        case I_CERT:
            checkCryptoPerm(thisSpi, key);
            thisSpi.engineInit(opmode, key, random);
            break;
        default:
            throw new AssertionError("Internal Cipher error: " + type);
        }
    }

    private void chooseProvider(int initType, int opmode, Key key,
            AlgorithmParameterSpec paramSpec,
            AlgorithmParameters params, SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        synchronized (lock) {
            if (spi != null) {
                implInit(spi, initType, opmode, key, paramSpec, params, random);
                return;
            }
            Exception lastException = null;
            while ((firstService != null) || serviceIterator.hasNext()) {
                Service s;
                CipherSpi thisSpi;
                if (firstService != null) {
                    s = firstService;
                    thisSpi = firstSpi;
                    firstService = null;
                    firstSpi = null;
                } else {
                    s = serviceIterator.next();
                    thisSpi = null;
                }
                // if provider says it does not support this key, ignore it
                if (s.supportsParameter(key) == false) {
                    continue;
                }
                if (JceSecurity.canUseProvider(s.getProvider()) == false) {
                    continue;
                }
                Transform tr = getTransform(s, transforms);
                if (tr == null) {
                    // should never happen
                    continue;
                }
                if (tr.supportsModePadding(s) == S_NO) {
                    continue;
                }
                try {
                    if (thisSpi == null) {
                        thisSpi = (CipherSpi)s.newInstance(null);
                    }
                    tr.setModePadding(thisSpi);
                    initCryptoPermission();
                    implInit(thisSpi, initType, opmode, key, paramSpec,
                                                        params, random);
                    provider = s.getProvider();
                    this.spi = thisSpi;
                    firstService = null;
                    serviceIterator = null;
                    transforms = null;
                    return;
                } catch (Exception e) {
                    // NoSuchAlgorithmException from newInstance()
                    // InvalidKeyException from init()
                    // RuntimeException (ProviderException) from init()
                    // SecurityException from crypto permission check
                    if (lastException == null) {
                        lastException = e;
                    }
                }
            }
            // no working provider found, fail
            if (lastException instanceof InvalidKeyException) {
                throw (InvalidKeyException)lastException;
            }
            if (lastException instanceof InvalidAlgorithmParameterException) {
                throw (InvalidAlgorithmParameterException)lastException;
            }
            if (lastException instanceof RuntimeException) {
                throw (RuntimeException)lastException;
            }
            String kName = (key != null) ? key.getClass().getName() : "(null)";
            throw new InvalidKeyException
                ("No installed provider supports this key: "
                + kName, lastException);
        }
    }

    /**
     * Returns the provider of this <code>Cipher</code> object.
     *
     * @return the provider of this <code>Cipher</code> object
     */
    public final Provider getProvider() {
        chooseFirstProvider();
        return this.provider;
    }

    /**
     * Returns the algorithm name of this <code>Cipher</code> object.
     *
     * <p>This is the same name that was specified in one of the
     * <code>getInstance</code> calls that created this <code>Cipher</code>
     * object..
     *
     * @return the algorithm name of this <code>Cipher</code> object.
     */
    public final String getAlgorithm() {
        return this.transformation;
    }

    /**
     * Returns the block size (in bytes).
     *
     * @return the block size (in bytes), or 0 if the underlying algorithm is
     * not a block cipher
     */
    public final int getBlockSize() {
        chooseFirstProvider();
        return spi.engineGetBlockSize();
    }

    /**
     * Returns the length in bytes that an output buffer would need to be in
     * order to hold the result of the next <code>update</code> or
     * <code>doFinal</code> operation, given the input length
     * <code>inputLen</code> (in bytes).
     *
     * <p>This call takes into account any unprocessed (buffered) data from a
     * previous <code>update</code> call, padding, and AEAD tagging.
     *
     * <p>The actual output length of the next <code>update</code> or
     * <code>doFinal</code> call may be smaller than the length returned by
     * this method.
     *
     * @param inputLen the input length (in bytes)
     *
     * @return the required output buffer size (in bytes)
     *
     * @exception IllegalStateException if this cipher is in a wrong state
     * (e.g., has not yet been initialized)
     */
    public final int getOutputSize(int inputLen) {

        if (!initialized && !(this instanceof NullCipher)) {
            throw new IllegalStateException("Cipher not initialized");
        }
        if (inputLen < 0) {
            throw new IllegalArgumentException("Input size must be equal " +
                                               "to or greater than zero");
        }
        chooseFirstProvider();
        return spi.engineGetOutputSize(inputLen);
    }

    /**
     * Returns the initialization vector (IV) in a new buffer.
     *
     * <p>This is useful in the case where a random IV was created,
     * or in the context of password-based encryption or
     * decryption, where the IV is derived from a user-supplied password.
     *
     * @return the initialization vector in a new buffer, or null if the
     * underlying algorithm does not use an IV, or if the IV has not yet
     * been set.
     */
    public final byte[] getIV() {
        chooseFirstProvider();
        return spi.engineGetIV();
    }

    /**
     * Returns the parameters used with this cipher.
     *
     * <p>The returned parameters may be the same that were used to initialize
     * this cipher, or may contain a combination of default and random
     * parameter values used by the underlying cipher implementation if this
     * cipher requires algorithm parameters but was not initialized with any.
     *
     * @return the parameters used with this cipher, or null if this cipher
     * does not use any parameters.
     */
    public final AlgorithmParameters getParameters() {
        chooseFirstProvider();
        return spi.engineGetParameters();
    }

    /**
     * Returns the exemption mechanism object used with this cipher.
     *
     * @return the exemption mechanism object used with this cipher, or
     * null if this cipher does not use any exemption mechanism.
     */
    public final ExemptionMechanism getExemptionMechanism() {
        chooseFirstProvider();
        return exmech;
    }

    //
    // Crypto permission check code below
    //
    private void checkCryptoPerm(CipherSpi checkSpi, Key key)
            throws InvalidKeyException {
        if (cryptoPerm == CryptoAllPermission.INSTANCE) {
            return;
        }
        // Check if key size and default parameters are within legal limits
        AlgorithmParameterSpec params;
        try {
            params = getAlgorithmParameterSpec(checkSpi.engineGetParameters());
        } catch (InvalidParameterSpecException ipse) {
            throw new InvalidKeyException
                ("Unsupported default algorithm parameters");
        }
        if (!passCryptoPermCheck(checkSpi, key, params)) {
            throw new InvalidKeyException(
                "Illegal key size or default parameters");
        }
    }

    private void checkCryptoPerm(CipherSpi checkSpi, Key key,
            AlgorithmParameterSpec params) throws InvalidKeyException,
            InvalidAlgorithmParameterException {
        if (cryptoPerm == CryptoAllPermission.INSTANCE) {
            return;
        }
        // Determine keysize and check if it is within legal limits
        if (!passCryptoPermCheck(checkSpi, key, null)) {
            throw new InvalidKeyException("Illegal key size");
        }
        if ((params != null) && (!passCryptoPermCheck(checkSpi, key, params))) {
            throw new InvalidAlgorithmParameterException("Illegal parameters");
        }
    }

    private void checkCryptoPerm(CipherSpi checkSpi, Key key,
            AlgorithmParameters params)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        if (cryptoPerm == CryptoAllPermission.INSTANCE) {
            return;
        }
        // Convert the specified parameters into specs and then delegate.
        AlgorithmParameterSpec pSpec;
        try {
            pSpec = getAlgorithmParameterSpec(params);
        } catch (InvalidParameterSpecException ipse) {
            throw new InvalidAlgorithmParameterException
                ("Failed to retrieve algorithm parameter specification");
        }
        checkCryptoPerm(checkSpi, key, pSpec);
    }

    private boolean passCryptoPermCheck(CipherSpi checkSpi, Key key,
                                        AlgorithmParameterSpec params)
            throws InvalidKeyException {
        String em = cryptoPerm.getExemptionMechanism();
        int keySize = checkSpi.engineGetKeySize(key);
        // Use the "algorithm" component of the cipher
        // transformation so that the perm check would
        // work when the key has the "aliased" algo.
        String algComponent;
        int index = transformation.indexOf('/');
        if (index != -1) {
            algComponent = transformation.substring(0, index);
        } else {
            algComponent = transformation;
        }
        CryptoPermission checkPerm =
            new CryptoPermission(algComponent, keySize, params, em);

        if (!cryptoPerm.implies(checkPerm)) {
            if (debug != null) {
                debug.println("Crypto Permission check failed");
                debug.println("granted: " + cryptoPerm);
                debug.println("requesting: " + checkPerm);
            }
            return false;
        }
        if (exmech == null) {
            return true;
        }
        try {
            if (!exmech.isCryptoAllowed(key)) {
                if (debug != null) {
                    debug.println(exmech.getName() + " isn't enforced");
                }
                return false;
            }
        } catch (ExemptionMechanismException eme) {
            if (debug != null) {
                debug.println("Cannot determine whether "+
                              exmech.getName() + " has been enforced");
                eme.printStackTrace();
            }
            return false;
        }
        return true;
    }

    // check if opmode is one of the defined constants
    // throw InvalidParameterExeption if not
    private static void checkOpmode(int opmode) {
        if ((opmode < ENCRYPT_MODE) || (opmode > UNWRAP_MODE)) {
            throw new InvalidParameterException("Invalid operation mode");
        }
    }

    /**
     * Initializes this cipher with a key.
     *
     * <p>The cipher is initialized for one of the following four operations:
     * encryption, decryption, key wrapping or key unwrapping, depending
     * on the value of <code>opmode</code>.
     *
     * <p>If this cipher requires any algorithm parameters that cannot be
     * derived from the given <code>key</code>, the underlying cipher
     * implementation is supposed to generate the required parameters itself
     * (using provider-specific default or random values) if it is being
     * initialized for encryption or key wrapping, and raise an
     * <code>InvalidKeyException</code> if it is being
     * initialized for decryption or key unwrapping.
     * The generated parameters can be retrieved using
     * {@link #getParameters() getParameters} or
     * {@link #getIV() getIV} (if the parameter is an IV).
     *
     * <p>If this cipher requires algorithm parameters that cannot be
     * derived from the input parameters, and there are no reasonable
     * provider-specific default values, initialization will
     * necessarily fail.
     *
     * <p>If this cipher (including its underlying feedback or padding scheme)
     * requires any random bytes (e.g., for parameter generation), it will get
     * them using the {@link SecureRandom <code>SecureRandom</code>}
     * implementation of the highest-priority
     * installed provider as the source of randomness.
     * (If none of the installed providers supply an implementation of
     * SecureRandom, a system-provided source of randomness will be used.)
     *
     * <p>Note that when a Cipher object is initialized, it loses all
     * previously-acquired state. In other words, initializing a Cipher is
     * equivalent to creating a new instance of that Cipher and initializing
     * it.
     *
     * @param opmode the operation mode of this cipher (this is one of
     * the following:
     * <code>ENCRYPT_MODE</code>, <code>DECRYPT_MODE</code>,
     * <code>WRAP_MODE</code> or <code>UNWRAP_MODE</code>)
     * @param key the key
     *
     * @exception InvalidKeyException if the given key is inappropriate for
     * initializing this cipher, or requires
     * algorithm parameters that cannot be
     * determined from the given key, or if the given key has a keysize that
     * exceeds the maximum allowable keysize (as determined from the
     * configured jurisdiction policy files).
     */
    public final void init(int opmode, Key key) throws InvalidKeyException {
        init(opmode, key, JceSecurity.RANDOM);
    }

    /**
     * Initializes this cipher with a key and a source of randomness.
     *
     * <p>The cipher is initialized for one of the following four operations:
     * encryption, decryption, key wrapping or  key unwrapping, depending
     * on the value of <code>opmode</code>.
     *
     * <p>If this cipher requires any algorithm parameters that cannot be
     * derived from the given <code>key</code>, the underlying cipher
     * implementation is supposed to generate the required parameters itself
     * (using provider-specific default or random values) if it is being
     * initialized for encryption or key wrapping, and raise an
     * <code>InvalidKeyException</code> if it is being
     * initialized for decryption or key unwrapping.
     * The generated parameters can be retrieved using
     * {@link #getParameters() getParameters} or
     * {@link #getIV() getIV} (if the parameter is an IV).
     *
     * <p>If this cipher requires algorithm parameters that cannot be
     * derived from the input parameters, and there are no reasonable
     * provider-specific default values, initialization will
     * necessarily fail.
     *
     * <p>If this cipher (including its underlying feedback or padding scheme)
     * requires any random bytes (e.g., for parameter generation), it will get
     * them from <code>random</code>.
     *
     * <p>Note that when a Cipher object is initialized, it loses all
     * previously-acquired state. In other words, initializing a Cipher is
     * equivalent to creating a new instance of that Cipher and initializing
     * it.
     *
     * @param opmode the operation mode of this cipher (this is one of the
     * following:
     * <code>ENCRYPT_MODE</code>, <code>DECRYPT_MODE</code>,
     * <code>WRAP_MODE</code> or <code>UNWRAP_MODE</code>)
     * @param key the encryption key
     * @param random the source of randomness
     *
     * @exception InvalidKeyException if the given key is inappropriate for
     * initializing this cipher, or requires
     * algorithm parameters that cannot be
     * determined from the given key, or if the given key has a keysize that
     * exceeds the maximum allowable keysize (as determined from the
     * configured jurisdiction policy files).
     */
    public final void init(int opmode, Key key, SecureRandom random)
            throws InvalidKeyException
    {
        initialized = false;
        checkOpmode(opmode);

        if (spi != null) {
            checkCryptoPerm(spi, key);
            spi.engineInit(opmode, key, random);
        } else {
            try {
                chooseProvider(I_KEY, opmode, key, null, null, random);
            } catch (InvalidAlgorithmParameterException e) {
                // should never occur
                throw new InvalidKeyException(e);
            }
        }

        initialized = true;
        this.opmode = opmode;
        
        /* CRYLOGGER */
        CRYLogger.write("[Cipher] init(int, Key, SecureRandom) called\n");
        CRYLogger.write("[Cipher] algorithm: " + this.transformation + "\n");
        CRYLogger.write("[Cipher] key.iv: ", this.getIV());
        CRYLogger.write("[Cipher] key.bits (" + this.transformation + "): " + sun.security.util.KeyUtil.getKeySize(key)  + "\n");
        CRYLogger.write("[Cipher] key.encoded: ", key.getEncoded());
        CRYLogger.write("[Cipher] rnd.class: " + random.getClass().getName() + "\n");
    }

    /**
     * Initializes this cipher with a key and a set of algorithm
     * parameters.
     *
     * <p>The cipher is initialized for one of the following four operations:
     * encryption, decryption, key wrapping or  key unwrapping, depending
     * on the value of <code>opmode</code>.
     *
     * <p>If this cipher requires any algorithm parameters and
     * <code>params</code> is null, the underlying cipher implementation is
     * supposed to generate the required parameters itself (using
     * provider-specific default or random values) if it is being
     * initialized for encryption or key wrapping, and raise an
     * <code>InvalidAlgorithmParameterException</code> if it is being
     * initialized for decryption or key unwrapping.
     * The generated parameters can be retrieved using
     * {@link #getParameters() getParameters} or
     * {@link #getIV() getIV} (if the parameter is an IV).
     *
     * <p>If this cipher requires algorithm parameters that cannot be
     * derived from the input parameters, and there are no reasonable
     * provider-specific default values, initialization will
     * necessarily fail.
     *
     * <p>If this cipher (including its underlying feedback or padding scheme)
     * requires any random bytes (e.g., for parameter generation), it will get
     * them using the {@link SecureRandom <code>SecureRandom</code>}
     * implementation of the highest-priority
     * installed provider as the source of randomness.
     * (If none of the installed providers supply an implementation of
     * SecureRandom, a system-provided source of randomness will be used.)
     *
     * <p>Note that when a Cipher object is initialized, it loses all
     * previously-acquired state. In other words, initializing a Cipher is
     * equivalent to creating a new instance of that Cipher and initializing
     * it.
     *
     * @param opmode the operation mode of this cipher (this is one of the
     * following:
     * <code>ENCRYPT_MODE</code>, <code>DECRYPT_MODE</code>,
     * <code>WRAP_MODE</code> or <code>UNWRAP_MODE</code>)
     * @param key the encryption key
     * @param params the algorithm parameters
     *
     * @exception InvalidKeyException if the given key is inappropriate for
     * initializing this cipher, or its keysize exceeds the maximum allowable
     * keysize (as determined from the configured jurisdiction policy files).
     * @exception InvalidAlgorithmParameterException if the given algorithm
     * parameters are inappropriate for this cipher,
     * or this cipher requires
     * algorithm parameters and <code>params</code> is null, or the given
     * algorithm parameters imply a cryptographic strength that would exceed
     * the legal limits (as determined from the configured jurisdiction
     * policy files).
     */
    public final void init(int opmode, Key key, AlgorithmParameterSpec params)
            throws InvalidKeyException, InvalidAlgorithmParameterException
    {
        init(opmode, key, params, JceSecurity.RANDOM);
    }

    /**
     * Initializes this cipher with a key, a set of algorithm
     * parameters, and a source of randomness.
     *
     * <p>The cipher is initialized for one of the following four operations:
     * encryption, decryption, key wrapping or  key unwrapping, depending
     * on the value of <code>opmode</code>.
     *
     * <p>If this cipher requires any algorithm parameters and
     * <code>params</code> is null, the underlying cipher implementation is
     * supposed to generate the required parameters itself (using
     * provider-specific default or random values) if it is being
     * initialized for encryption or key wrapping, and raise an
     * <code>InvalidAlgorithmParameterException</code> if it is being
     * initialized for decryption or key unwrapping.
     * The generated parameters can be retrieved using
     * {@link #getParameters() getParameters} or
     * {@link #getIV() getIV} (if the parameter is an IV).
     *
     * <p>If this cipher requires algorithm parameters that cannot be
     * derived from the input parameters, and there are no reasonable
     * provider-specific default values, initialization will
     * necessarily fail.
     *
     * <p>If this cipher (including its underlying feedback or padding scheme)
     * requires any random bytes (e.g., for parameter generation), it will get
     * them from <code>random</code>.
     *
     * <p>Note that when a Cipher object is initialized, it loses all
     * previously-acquired state. In other words, initializing a Cipher is
     * equivalent to creating a new instance of that Cipher and initializing
     * it.
     *
     * @param opmode the operation mode of this cipher (this is one of the
     * following:
     * <code>ENCRYPT_MODE</code>, <code>DECRYPT_MODE</code>,
     * <code>WRAP_MODE</code> or <code>UNWRAP_MODE</code>)
     * @param key the encryption key
     * @param params the algorithm parameters
     * @param random the source of randomness
     *
     * @exception InvalidKeyException if the given key is inappropriate for
     * initializing this cipher, or its keysize exceeds the maximum allowable
     * keysize (as determined from the configured jurisdiction policy files).
     * @exception InvalidAlgorithmParameterException if the given algorithm
     * parameters are inappropriate for this cipher,
     * or this cipher requires
     * algorithm parameters and <code>params</code> is null, or the given
     * algorithm parameters imply a cryptographic strength that would exceed
     * the legal limits (as determined from the configured jurisdiction
     * policy files).
     */
    public final void init(int opmode, Key key, AlgorithmParameterSpec params,
                           SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException
    {
        initialized = false;
        checkOpmode(opmode);

        if (spi != null) {
            checkCryptoPerm(spi, key, params);
            spi.engineInit(opmode, key, params, random);
        } else {
            chooseProvider(I_PARAMSPEC, opmode, key, params, null, random);
        }

        initialized = true;
        this.opmode = opmode;
        
        /* CRYLOGGER */
        CRYLogger.write("[Cipher] init(int, Key, AlgorithmParameterSpec, SecureRandom) called\n");
        CRYLogger.write("[Cipher] algorithm: " + this.transformation + "\n");
        CRYLogger.write("[Cipher] key.iv: ", this.getIV());
        CRYLogger.write("[Cipher] key.bits (" + this.transformation + "): " + sun.security.util.KeyUtil.getKeySize(key)  + "\n");
        CRYLogger.write("[Cipher] key.encoded: ", key.getEncoded());
        CRYLogger.write("[Cipher] rnd.class: " + random.getClass().getName() + "\n");
    }

    /**
     * Initializes this cipher with a key and a set of algorithm
     * parameters.
     *
     * <p>The cipher is initialized for one of the following four operations:
     * encryption, decryption, key wrapping or  key unwrapping, depending
     * on the value of <code>opmode</code>.
     *
     * <p>If this cipher requires any algorithm parameters and
     * <code>params</code> is null, the underlying cipher implementation is
     * supposed to generate the required parameters itself (using
     * provider-specific default or random values) if it is being
     * initialized for encryption or key wrapping, and raise an
     * <code>InvalidAlgorithmParameterException</code> if it is being
     * initialized for decryption or key unwrapping.
     * The generated parameters can be retrieved using
     * {@link #getParameters() getParameters} or
     * {@link #getIV() getIV} (if the parameter is an IV).
     *
     * <p>If this cipher requires algorithm parameters that cannot be
     * derived from the input parameters, and there are no reasonable
     * provider-specific default values, initialization will
     * necessarily fail.
     *
     * <p>If this cipher (including its underlying feedback or padding scheme)
     * requires any random bytes (e.g., for parameter generation), it will get
     * them using the {@link SecureRandom <code>SecureRandom</code>}
     * implementation of the highest-priority
     * installed provider as the source of randomness.
     * (If none of the installed providers supply an implementation of
     * SecureRandom, a system-provided source of randomness will be used.)
     *
     * <p>Note that when a Cipher object is initialized, it loses all
     * previously-acquired state. In other words, initializing a Cipher is
     * equivalent to creating a new instance of that Cipher and initializing
     * it.
     *
     * @param opmode the operation mode of this cipher (this is one of the
     * following: <code>ENCRYPT_MODE</code>,
     * <code>DECRYPT_MODE</code>, <code>WRAP_MODE</code>
     * or <code>UNWRAP_MODE</code>)
     * @param key the encryption key
     * @param params the algorithm parameters
     *
     * @exception InvalidKeyException if the given key is inappropriate for
     * initializing this cipher, or its keysize exceeds the maximum allowable
     * keysize (as determined from the configured jurisdiction policy files).
     * @exception InvalidAlgorithmParameterException if the given algorithm
     * parameters are inappropriate for this cipher,
     * or this cipher requires
     * algorithm parameters and <code>params</code> is null, or the given
     * algorithm parameters imply a cryptographic strength that would exceed
     * the legal limits (as determined from the configured jurisdiction
     * policy files).
     */
    public final void init(int opmode, Key key, AlgorithmParameters params)
            throws InvalidKeyException, InvalidAlgorithmParameterException
    {
        init(opmode, key, params, JceSecurity.RANDOM);
    }

    /**
     * Initializes this cipher with a key, a set of algorithm
     * parameters, and a source of randomness.
     *
     * <p>The cipher is initialized for one of the following four operations:
     * encryption, decryption, key wrapping or  key unwrapping, depending
     * on the value of <code>opmode</code>.
     *
     * <p>If this cipher requires any algorithm parameters and
     * <code>params</code> is null, the underlying cipher implementation is
     * supposed to generate the required parameters itself (using
     * provider-specific default or random values) if it is being
     * initialized for encryption or key wrapping, and raise an
     * <code>InvalidAlgorithmParameterException</code> if it is being
     * initialized for decryption or key unwrapping.
     * The generated parameters can be retrieved using
     * {@link #getParameters() getParameters} or
     * {@link #getIV() getIV} (if the parameter is an IV).
     *
     * <p>If this cipher requires algorithm parameters that cannot be
     * derived from the input parameters, and there are no reasonable
     * provider-specific default values, initialization will
     * necessarily fail.
     *
     * <p>If this cipher (including its underlying feedback or padding scheme)
     * requires any random bytes (e.g., for parameter generation), it will get
     * them from <code>random</code>.
     *
     * <p>Note that when a Cipher object is initialized, it loses all
     * previously-acquired state. In other words, initializing a Cipher is
     * equivalent to creating a new instance of that Cipher and initializing
     * it.
     *
     * @param opmode the operation mode of this cipher (this is one of the
     * following: <code>ENCRYPT_MODE</code>,
     * <code>DECRYPT_MODE</code>, <code>WRAP_MODE</code>
     * or <code>UNWRAP_MODE</code>)
     * @param key the encryption key
     * @param params the algorithm parameters
     * @param random the source of randomness
     *
     * @exception InvalidKeyException if the given key is inappropriate for
     * initializing this cipher, or its keysize exceeds the maximum allowable
     * keysize (as determined from the configured jurisdiction policy files).
     * @exception InvalidAlgorithmParameterException if the given algorithm
     * parameters are inappropriate for this cipher,
     * or this cipher requires
     * algorithm parameters and <code>params</code> is null, or the given
     * algorithm parameters imply a cryptographic strength that would exceed
     * the legal limits (as determined from the configured jurisdiction
     * policy files).
     */
    public final void init(int opmode, Key key, AlgorithmParameters params,
                           SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException
    {
        initialized = false;
        checkOpmode(opmode);

        if (spi != null) {
            checkCryptoPerm(spi, key, params);
            spi.engineInit(opmode, key, params, random);
        } else {
            chooseProvider(I_PARAMS, opmode, key, null, params, random);
        }

        initialized = true;
        this.opmode = opmode;
        
        /* CRYLOGGER */
        CRYLogger.write("[Cipher] init(int, Key, AlgorithmParameters, SecureRandom) called\n");
        CRYLogger.write("[Cipher] algorithm: " + this.transformation + "\n");
        CRYLogger.write("[Cipher] key.iv: ", this.getIV());
        CRYLogger.write("[Cipher] key.bits (" + this.transformation + "): " + sun.security.util.KeyUtil.getKeySize(key)  + "\n");
        CRYLogger.write("[Cipher] key.encoded: ", key.getEncoded());
        CRYLogger.write("[Cipher] rnd.class: " + random.getClass().getName() + "\n");
    }

    /**
     * Initializes this cipher with the public key from the given certificate.
     * <p> The cipher is initialized for one of the following four operations:
     * encryption, decryption, key wrapping or  key unwrapping, depending
     * on the value of <code>opmode</code>.
     *
     * <p>If the certificate is of type X.509 and has a <i>key usage</i>
     * extension field marked as critical, and the value of the <i>key usage</i>
     * extension field implies that the public key in
     * the certificate and its corresponding private key are not
     * supposed to be used for the operation represented by the value
     * of <code>opmode</code>,
     * an <code>InvalidKeyException</code>
     * is thrown.
     *
     * <p> If this cipher requires any algorithm parameters that cannot be
     * derived from the public key in the given certificate, the underlying
     * cipher
     * implementation is supposed to generate the required parameters itself
     * (using provider-specific default or random values) if it is being
     * initialized for encryption or key wrapping, and raise an <code>
     * InvalidKeyException</code> if it is being initialized for decryption or
     * key unwrapping.
     * The generated parameters can be retrieved using
     * {@link #getParameters() getParameters} or
     * {@link #getIV() getIV} (if the parameter is an IV).
     *
     * <p>If this cipher requires algorithm parameters that cannot be
     * derived from the input parameters, and there are no reasonable
     * provider-specific default values, initialization will
     * necessarily fail.
     *
     * <p>If this cipher (including its underlying feedback or padding scheme)
     * requires any random bytes (e.g., for parameter generation), it will get
     * them using the
     * <code>SecureRandom</code>
     * implementation of the highest-priority
     * installed provider as the source of randomness.
     * (If none of the installed providers supply an implementation of
     * SecureRandom, a system-provided source of randomness will be used.)
     *
     * <p>Note that when a Cipher object is initialized, it loses all
     * previously-acquired state. In other words, initializing a Cipher is
     * equivalent to creating a new instance of that Cipher and initializing
     * it.
     *
     * @param opmode the operation mode of this cipher (this is one of the
     * following:
     * <code>ENCRYPT_MODE</code>, <code>DECRYPT_MODE</code>,
     * <code>WRAP_MODE</code> or <code>UNWRAP_MODE</code>)
     * @param certificate the certificate
     *
     * @exception InvalidKeyException if the public key in the given
     * certificate is inappropriate for initializing this cipher, or this
     * cipher requires algorithm parameters that cannot be determined from the
     * public key in the given certificate, or the keysize of the public key
     * in the given certificate has a keysize that exceeds the maximum
     * allowable keysize (as determined by the configured jurisdiction policy
     * files).
     */
    public final void init(int opmode, Certificate certificate)
            throws InvalidKeyException
    {
        init(opmode, certificate, JceSecurity.RANDOM);
    }

    /**
     * Initializes this cipher with the public key from the given certificate
     * and
     * a source of randomness.
     *
     * <p>The cipher is initialized for one of the following four operations:
     * encryption, decryption, key wrapping
     * or key unwrapping, depending on
     * the value of <code>opmode</code>.
     *
     * <p>If the certificate is of type X.509 and has a <i>key usage</i>
     * extension field marked as critical, and the value of the <i>key usage</i>
     * extension field implies that the public key in
     * the certificate and its corresponding private key are not
     * supposed to be used for the operation represented by the value of
     * <code>opmode</code>,
     * an <code>InvalidKeyException</code>
     * is thrown.
     *
     * <p>If this cipher requires any algorithm parameters that cannot be
     * derived from the public key in the given <code>certificate</code>,
     * the underlying cipher
     * implementation is supposed to generate the required parameters itself
     * (using provider-specific default or random values) if it is being
     * initialized for encryption or key wrapping, and raise an
     * <code>InvalidKeyException</code> if it is being
     * initialized for decryption or key unwrapping.
     * The generated parameters can be retrieved using
     * {@link #getParameters() getParameters} or
     * {@link #getIV() getIV} (if the parameter is an IV).
     *
     * <p>If this cipher requires algorithm parameters that cannot be
     * derived from the input parameters, and there are no reasonable
     * provider-specific default values, initialization will
     * necessarily fail.
     *
     * <p>If this cipher (including its underlying feedback or padding scheme)
     * requires any random bytes (e.g., for parameter generation), it will get
     * them from <code>random</code>.
     *
     * <p>Note that when a Cipher object is initialized, it loses all
     * previously-acquired state. In other words, initializing a Cipher is
     * equivalent to creating a new instance of that Cipher and initializing
     * it.
     *
     * @param opmode the operation mode of this cipher (this is one of the
     * following:
     * <code>ENCRYPT_MODE</code>, <code>DECRYPT_MODE</code>,
     * <code>WRAP_MODE</code> or <code>UNWRAP_MODE</code>)
     * @param certificate the certificate
     * @param random the source of randomness
     *
     * @exception InvalidKeyException if the public key in the given
     * certificate is inappropriate for initializing this cipher, or this
     * cipher
     * requires algorithm parameters that cannot be determined from the
     * public key in the given certificate, or the keysize of the public key
     * in the given certificate has a keysize that exceeds the maximum
     * allowable keysize (as determined by the configured jurisdiction policy
     * files).
     */
    public final void init(int opmode, Certificate certificate,
                           SecureRandom random)
            throws InvalidKeyException
    {
        initialized = false;
        checkOpmode(opmode);

        // Check key usage if the certificate is of
        // type X.509.
        if (certificate instanceof java.security.cert.X509Certificate) {
            // Check whether the cert has a key usage extension
            // marked as a critical extension.
            X509Certificate cert = (X509Certificate)certificate;
            Set<String> critSet = cert.getCriticalExtensionOIDs();

            if (critSet != null && !critSet.isEmpty()
                && critSet.contains(KEY_USAGE_EXTENSION_OID)) {
                boolean[] keyUsageInfo = cert.getKeyUsage();
                // keyUsageInfo[2] is for keyEncipherment;
                // keyUsageInfo[3] is for dataEncipherment.
                if ((keyUsageInfo != null) &&
                    (((opmode == Cipher.ENCRYPT_MODE) &&
                      (keyUsageInfo.length > 3) &&
                      (keyUsageInfo[3] == false)) ||
                     ((opmode == Cipher.WRAP_MODE) &&
                      (keyUsageInfo.length > 2) &&
                      (keyUsageInfo[2] == false)))) {
                    throw new InvalidKeyException("Wrong key usage");
                }
            }
        }

        PublicKey publicKey =
            (certificate==null? null:certificate.getPublicKey());

        if (spi != null) {
            checkCryptoPerm(spi, publicKey);
            spi.engineInit(opmode, publicKey, random);
        } else {
            try {
                chooseProvider(I_CERT, opmode, publicKey, null, null, random);
            } catch (InvalidAlgorithmParameterException e) {
                // should never occur
                throw new InvalidKeyException(e);
            }
        }

        initialized = true;
        this.opmode = opmode;
        
        /* CRYLOGGER */
        CRYLogger.write("[Cipher] init(int, Certificate, SecureRandom) called\n");
        CRYLogger.write("[Cipher] algorithm: " + this.transformation + "\n");
    }

    /**
     * Ensures that Cipher is in a valid state for update() and doFinal()
     * calls - should be initialized and in ENCRYPT_MODE or DECRYPT_MODE.
     * @throws IllegalStateException if Cipher object is not in valid state.
     */
    private void checkCipherState() {
        if (!(this instanceof NullCipher)) {
            if (!initialized) {
                throw new IllegalStateException("Cipher not initialized");
            }
            if ((opmode != Cipher.ENCRYPT_MODE) &&
                (opmode != Cipher.DECRYPT_MODE)) {
                throw new IllegalStateException("Cipher not initialized " +
                                                "for encryption/decryption");
            }
        }
    }

    /**
     * Continues a multiple-part encryption or decryption operation
     * (depending on how this cipher was initialized), processing another data
     * part.
     *
     * <p>The bytes in the <code>input</code> buffer are processed, and the
     * result is stored in a new buffer.
     *
     * <p>If <code>input</code> has a length of zero, this method returns
     * <code>null</code>.
     *
     * @param input the input buffer
     *
     * @return the new buffer with the result, or null if the underlying
     * cipher is a block cipher and the input data is too short to result in a
     * new block.
     *
     * @exception IllegalStateException if this cipher is in a wrong state
     * (e.g., has not been initialized)
     */
    public final byte[] update(byte[] input) {
        checkCipherState();

        // Input sanity check
        if (input == null) {
            throw new IllegalArgumentException("Null input buffer");
        }

        chooseFirstProvider();
        if (input.length == 0) {
            return null;
        }
        return spi.engineUpdate(input, 0, input.length);
    }

    /**
     * Continues a multiple-part encryption or decryption operation
     * (depending on how this cipher was initialized), processing another data
     * part.
     *
     * <p>The first <code>inputLen</code> bytes in the <code>input</code>
     * buffer, starting at <code>inputOffset</code> inclusive, are processed,
     * and the result is stored in a new buffer.
     *
     * <p>If <code>inputLen</code> is zero, this method returns
     * <code>null</code>.
     *
     * @param input the input buffer
     * @param inputOffset the offset in <code>input</code> where the input
     * starts
     * @param inputLen the input length
     *
     * @return the new buffer with the result, or null if the underlying
     * cipher is a block cipher and the input data is too short to result in a
     * new block.
     *
     * @exception IllegalStateException if this cipher is in a wrong state
     * (e.g., has not been initialized)
     */
    public final byte[] update(byte[] input, int inputOffset, int inputLen) {
        checkCipherState();

        // Input sanity check
        if (input == null || inputOffset < 0
            || inputLen > (input.length - inputOffset) || inputLen < 0) {
            throw new IllegalArgumentException("Bad arguments");
        }

        chooseFirstProvider();
        if (inputLen == 0) {
            return null;
        }
        return spi.engineUpdate(input, inputOffset, inputLen);
    }

    /**
     * Continues a multiple-part encryption or decryption operation
     * (depending on how this cipher was initialized), processing another data
     * part.
     *
     * <p>The first <code>inputLen</code> bytes in the <code>input</code>
     * buffer, starting at <code>inputOffset</code> inclusive, are processed,
     * and the result is stored in the <code>output</code> buffer.
     *
     * <p>If the <code>output</code> buffer is too small to hold the result,
     * a <code>ShortBufferException</code> is thrown. In this case, repeat this
     * call with a larger output buffer. Use
     * {@link #getOutputSize(int) getOutputSize} to determine how big
     * the output buffer should be.
     *
     * <p>If <code>inputLen</code> is zero, this method returns
     * a length of zero.
     *
     * <p>Note: this method should be copy-safe, which means the
     * <code>input</code> and <code>output</code> buffers can reference
     * the same byte array and no unprocessed input data is overwritten
     * when the result is copied into the output buffer.
     *
     * @param input the input buffer
     * @param inputOffset the offset in <code>input</code> where the input
     * starts
     * @param inputLen the input length
     * @param output the buffer for the result
     *
     * @return the number of bytes stored in <code>output</code>
     *
     * @exception IllegalStateException if this cipher is in a wrong state
     * (e.g., has not been initialized)
     * @exception ShortBufferException if the given output buffer is too small
     * to hold the result
     */
    public final int update(byte[] input, int inputOffset, int inputLen,
                            byte[] output)
            throws ShortBufferException {
        checkCipherState();

        // Input sanity check
        if (input == null || inputOffset < 0
            || inputLen > (input.length - inputOffset) || inputLen < 0) {
            throw new IllegalArgumentException("Bad arguments");
        }

        chooseFirstProvider();
        if (inputLen == 0) {
            return 0;
        }
        return spi.engineUpdate(input, inputOffset, inputLen,
                                      output, 0);
    }

    /**
     * Continues a multiple-part encryption or decryption operation
     * (depending on how this cipher was initialized), processing another data
     * part.
     *
     * <p>The first <code>inputLen</code> bytes in the <code>input</code>
     * buffer, starting at <code>inputOffset</code> inclusive, are processed,
     * and the result is stored in the <code>output</code> buffer, starting at
     * <code>outputOffset</code> inclusive.
     *
     * <p>If the <code>output</code> buffer is too small to hold the result,
     * a <code>ShortBufferException</code> is thrown. In this case, repeat this
     * call with a larger output buffer. Use
     * {@link #getOutputSize(int) getOutputSize} to determine how big
     * the output buffer should be.
     *
     * <p>If <code>inputLen</code> is zero, this method returns
     * a length of zero.
     *
     * <p>Note: this method should be copy-safe, which means the
     * <code>input</code> and <code>output</code> buffers can reference
     * the same byte array and no unprocessed input data is overwritten
     * when the result is copied into the output buffer.
     *
     * @param input the input buffer
     * @param inputOffset the offset in <code>input</code> where the input
     * starts
     * @param inputLen the input length
     * @param output the buffer for the result
     * @param outputOffset the offset in <code>output</code> where the result
     * is stored
     *
     * @return the number of bytes stored in <code>output</code>
     *
     * @exception IllegalStateException if this cipher is in a wrong state
     * (e.g., has not been initialized)
     * @exception ShortBufferException if the given output buffer is too small
     * to hold the result
     */
    public final int update(byte[] input, int inputOffset, int inputLen,
                            byte[] output, int outputOffset)
            throws ShortBufferException {
        checkCipherState();

        // Input sanity check
        if (input == null || inputOffset < 0
            || inputLen > (input.length - inputOffset) || inputLen < 0
            || outputOffset < 0) {
            throw new IllegalArgumentException("Bad arguments");
        }

        chooseFirstProvider();
        if (inputLen == 0) {
            return 0;
        }
        return spi.engineUpdate(input, inputOffset, inputLen,
                                      output, outputOffset);
    }

    /**
     * Continues a multiple-part encryption or decryption operation
     * (depending on how this cipher was initialized), processing another data
     * part.
     *
     * <p>All <code>input.remaining()</code> bytes starting at
     * <code>input.position()</code> are processed. The result is stored
     * in the output buffer.
     * Upon return, the input buffer's position will be equal
     * to its limit; its limit will not have changed. The output buffer's
     * position will have advanced by n, where n is the value returned
     * by this method; the output buffer's limit will not have changed.
     *
     * <p>If <code>output.remaining()</code> bytes are insufficient to
     * hold the result, a <code>ShortBufferException</code> is thrown.
     * In this case, repeat this call with a larger output buffer. Use
     * {@link #getOutputSize(int) getOutputSize} to determine how big
     * the output buffer should be.
     *
     * <p>Note: this method should be copy-safe, which means the
     * <code>input</code> and <code>output</code> buffers can reference
     * the same block of memory and no unprocessed input data is overwritten
     * when the result is copied into the output buffer.
     *
     * @param input the input ByteBuffer
     * @param output the output ByteByffer
     *
     * @return the number of bytes stored in <code>output</code>
     *
     * @exception IllegalStateException if this cipher is in a wrong state
     * (e.g., has not been initialized)
     * @exception IllegalArgumentException if input and output are the
     *   same object
     * @exception ReadOnlyBufferException if the output buffer is read-only
     * @exception ShortBufferException if there is insufficient space in the
     * output buffer
     * @since 1.5
     */
    public final int update(ByteBuffer input, ByteBuffer output)
            throws ShortBufferException {
        checkCipherState();

        if ((input == null) || (output == null)) {
            throw new IllegalArgumentException("Buffers must not be null");
        }
        if (input == output) {
            throw new IllegalArgumentException("Input and output buffers must "
                + "not be the same object, consider using buffer.duplicate()");
        }
        if (output.isReadOnly()) {
            throw new ReadOnlyBufferException();
        }

        chooseFirstProvider();
        return spi.engineUpdate(input, output);
    }

    /**
     * Finishes a multiple-part encryption or decryption operation, depending
     * on how this cipher was initialized.
     *
     * <p>Input data that may have been buffered during a previous
     * <code>update</code> operation is processed, with padding (if requested)
     * being applied.
     * If an AEAD mode such as GCM/CCM is being used, the authentication
     * tag is appended in the case of encryption, or verified in the
     * case of decryption.
     * The result is stored in a new buffer.
     *
     * <p>Upon finishing, this method resets this cipher object to the state
     * it was in when previously initialized via a call to <code>init</code>.
     * That is, the object is reset and available to encrypt or decrypt
     * (depending on the operation mode that was specified in the call to
     * <code>init</code>) more data.
     *
     * <p>Note: if any exception is thrown, this cipher object may need to
     * be reset before it can be used again.
     *
     * @return the new buffer with the result
     *
     * @exception IllegalStateException if this cipher is in a wrong state
     * (e.g., has not been initialized)
     * @exception IllegalBlockSizeException if this cipher is a block cipher,
     * no padding has been requested (only in encryption mode), and the total
     * input length of the data processed by this cipher is not a multiple of
     * block size; or if this encryption algorithm is unable to
     * process the input data provided.
     * @exception BadPaddingException if this cipher is in decryption mode,
     * and (un)padding has been requested, but the decrypted data is not
     * bounded by the appropriate padding bytes
     * @exception AEADBadTagException if this cipher is decrypting in an
     * AEAD mode (such as GCM/CCM), and the received authentication tag
     * does not match the calculated value
     */
    public final byte[] doFinal()
            throws IllegalBlockSizeException, BadPaddingException {
        checkCipherState();

        chooseFirstProvider();
//        return spi.engineDoFinal(null, 0, 0);
        
        /* CRYLOGGER */
        byte[] tmp = spi.engineDoFinal(null, 0, 0);
        CRYLogger.write("[Cipher] doFinal(void) called\n");
        CRYLogger.write("[Cipher] out bytes: " + tmp.length +
                " with " + transformation + "\n");
        return tmp;
    }

    /**
     * Finishes a multiple-part encryption or decryption operation, depending
     * on how this cipher was initialized.
     *
     * <p>Input data that may have been buffered during a previous
     * <code>update</code> operation is processed, with padding (if requested)
     * being applied.
     * If an AEAD mode such as GCM/CCM is being used, the authentication
     * tag is appended in the case of encryption, or verified in the
     * case of decryption.
     * The result is stored in the <code>output</code> buffer, starting at
     * <code>outputOffset</code> inclusive.
     *
     * <p>If the <code>output</code> buffer is too small to hold the result,
     * a <code>ShortBufferException</code> is thrown. In this case, repeat this
     * call with a larger output buffer. Use
     * {@link #getOutputSize(int) getOutputSize} to determine how big
     * the output buffer should be.
     *
     * <p>Upon finishing, this method resets this cipher object to the state
     * it was in when previously initialized via a call to <code>init</code>.
     * That is, the object is reset and available to encrypt or decrypt
     * (depending on the operation mode that was specified in the call to
     * <code>init</code>) more data.
     *
     * <p>Note: if any exception is thrown, this cipher object may need to
     * be reset before it can be used again.
     *
     * @param output the buffer for the result
     * @param outputOffset the offset in <code>output</code> where the result
     * is stored
     *
     * @return the number of bytes stored in <code>output</code>
     *
     * @exception IllegalStateException if this cipher is in a wrong state
     * (e.g., has not been initialized)
     * @exception IllegalBlockSizeException if this cipher is a block cipher,
     * no padding has been requested (only in encryption mode), and the total
     * input length of the data processed by this cipher is not a multiple of
     * block size; or if this encryption algorithm is unable to
     * process the input data provided.
     * @exception ShortBufferException if the given output buffer is too small
     * to hold the result
     * @exception BadPaddingException if this cipher is in decryption mode,
     * and (un)padding has been requested, but the decrypted data is not
     * bounded by the appropriate padding bytes
     * @exception AEADBadTagException if this cipher is decrypting in an
     * AEAD mode (such as GCM/CCM), and the received authentication tag
     * does not match the calculated value
     */
    public final int doFinal(byte[] output, int outputOffset)
            throws IllegalBlockSizeException, ShortBufferException,
               BadPaddingException {
        checkCipherState();

        // Input sanity check
        if ((output == null) || (outputOffset < 0)) {
            throw new IllegalArgumentException("Bad arguments");
        }

        chooseFirstProvider();
//        return spi.engineDoFinal(null, 0, 0, output, outputOffset);
        
        /* CRYLOGGER */
        int tmp = spi.engineDoFinal(null, 0, 0, output, outputOffset);
        CRYLogger.write("[Cipher] doFinal(byte[], int) called\n");
        CRYLogger.write("[Cipher] out bytes: " + tmp +
                " with " + transformation + "\n");
        return tmp;
    }

    /**
     * Encrypts or decrypts data in a single-part operation, or finishes a
     * multiple-part operation. The data is encrypted or decrypted,
     * depending on how this cipher was initialized.
     *
     * <p>The bytes in the <code>input</code> buffer, and any input bytes that
     * may have been buffered during a previous <code>update</code> operation,
     * are processed, with padding (if requested) being applied.
     * If an AEAD mode such as GCM/CCM is being used, the authentication
     * tag is appended in the case of encryption, or verified in the
     * case of decryption.
     * The result is stored in a new buffer.
     *
     * <p>Upon finishing, this method resets this cipher object to the state
     * it was in when previously initialized via a call to <code>init</code>.
     * That is, the object is reset and available to encrypt or decrypt
     * (depending on the operation mode that was specified in the call to
     * <code>init</code>) more data.
     *
     * <p>Note: if any exception is thrown, this cipher object may need to
     * be reset before it can be used again.
     *
     * @param input the input buffer
     *
     * @return the new buffer with the result
     *
     * @exception IllegalStateException if this cipher is in a wrong state
     * (e.g., has not been initialized)
     * @exception IllegalBlockSizeException if this cipher is a block cipher,
     * no padding has been requested (only in encryption mode), and the total
     * input length of the data processed by this cipher is not a multiple of
     * block size; or if this encryption algorithm is unable to
     * process the input data provided.
     * @exception BadPaddingException if this cipher is in decryption mode,
     * and (un)padding has been requested, but the decrypted data is not
     * bounded by the appropriate padding bytes
     * @exception AEADBadTagException if this cipher is decrypting in an
     * AEAD mode (such as GCM/CCM), and the received authentication tag
     * does not match the calculated value
     */
    public final byte[] doFinal(byte[] input)
            throws IllegalBlockSizeException, BadPaddingException {
        checkCipherState();

        // Input sanity check
        if (input == null) {
            throw new IllegalArgumentException("Null input buffer");
        }

        chooseFirstProvider();
//        return spi.engineDoFinal(input, 0, input.length);
        
        byte[] tmp = spi.engineDoFinal(input, 0, input.length);
        CRYLogger.write("[Cipher] doFinal(byte[] input) called\n");
        CRYLogger.write("[Cipher] out bytes: " + tmp.length +
                " with " + transformation + "\n");
        return tmp;
    }

    /**
     * Encrypts or decrypts data in a single-part operation, or finishes a
     * multiple-part operation. The data is encrypted or decrypted,
     * depending on how this cipher was initialized.
     *
     * <p>The first <code>inputLen</code> bytes in the <code>input</code>
     * buffer, starting at <code>inputOffset</code> inclusive, and any input
     * bytes that may have been buffered during a previous <code>update</code>
     * operation, are processed, with padding (if requested) being applied.
     * If an AEAD mode such as GCM/CCM is being used, the authentication
     * tag is appended in the case of encryption, or verified in the
     * case of decryption.
     * The result is stored in a new buffer.
     *
     * <p>Upon finishing, this method resets this cipher object to the state
     * it was in when previously initialized via a call to <code>init</code>.
     * That is, the object is reset and available to encrypt or decrypt
     * (depending on the operation mode that was specified in the call to
     * <code>init</code>) more data.
     *
     * <p>Note: if any exception is thrown, this cipher object may need to
     * be reset before it can be used again.
     *
     * @param input the input buffer
     * @param inputOffset the offset in <code>input</code> where the input
     * starts
     * @param inputLen the input length
     *
     * @return the new buffer with the result
     *
     * @exception IllegalStateException if this cipher is in a wrong state
     * (e.g., has not been initialized)
     * @exception IllegalBlockSizeException if this cipher is a block cipher,
     * no padding has been requested (only in encryption mode), and the total
     * input length of the data processed by this cipher is not a multiple of
     * block size; or if this encryption algorithm is unable to
     * process the input data provided.
     * @exception BadPaddingException if this cipher is in decryption mode,
     * and (un)padding has been requested, but the decrypted data is not
     * bounded by the appropriate padding bytes
     * @exception AEADBadTagException if this cipher is decrypting in an
     * AEAD mode (such as GCM/CCM), and the received authentication tag
     * does not match the calculated value
     */
    public final byte[] doFinal(byte[] input, int inputOffset, int inputLen)
            throws IllegalBlockSizeException, BadPaddingException {
        checkCipherState();

        // Input sanity check
        if (input == null || inputOffset < 0
            || inputLen > (input.length - inputOffset) || inputLen < 0) {
            throw new IllegalArgumentException("Bad arguments");
        }

        chooseFirstProvider();
//        return spi.engineDoFinal(input, inputOffset, inputLen);
        
        /* CRYLOGGER */
        byte[] tmp = spi.engineDoFinal(input, inputOffset, inputLen);
        CRYLogger.write("[Cipher] doFinal(byte[], int, int) called\n");
        CRYLogger.write("[Cipher] out bytes: " + tmp.length +
                " with " + transformation + "\n");
        return tmp;
    }

    /**
     * Encrypts or decrypts data in a single-part operation, or finishes a
     * multiple-part operation. The data is encrypted or decrypted,
     * depending on how this cipher was initialized.
     *
     * <p>The first <code>inputLen</code> bytes in the <code>input</code>
     * buffer, starting at <code>inputOffset</code> inclusive, and any input
     * bytes that may have been buffered during a previous <code>update</code>
     * operation, are processed, with padding (if requested) being applied.
     * If an AEAD mode such as GCM/CCM is being used, the authentication
     * tag is appended in the case of encryption, or verified in the
     * case of decryption.
     * The result is stored in the <code>output</code> buffer.
     *
     * <p>If the <code>output</code> buffer is too small to hold the result,
     * a <code>ShortBufferException</code> is thrown. In this case, repeat this
     * call with a larger output buffer. Use
     * {@link #getOutputSize(int) getOutputSize} to determine how big
     * the output buffer should be.
     *
     * <p>Upon finishing, this method resets this cipher object to the state
     * it was in when previously initialized via a call to <code>init</code>.
     * That is, the object is reset and available to encrypt or decrypt
     * (depending on the operation mode that was specified in the call to
     * <code>init</code>) more data.
     *
     * <p>Note: if any exception is thrown, this cipher object may need to
     * be reset before it can be used again.
     *
     * <p>Note: this method should be copy-safe, which means the
     * <code>input</code> and <code>output</code> buffers can reference
     * the same byte array and no unprocessed input data is overwritten
     * when the result is copied into the output buffer.
     *
     * @param input the input buffer
     * @param inputOffset the offset in <code>input</code> where the input
     * starts
     * @param inputLen the input length
     * @param output the buffer for the result
     *
     * @return the number of bytes stored in <code>output</code>
     *
     * @exception IllegalStateException if this cipher is in a wrong state
     * (e.g., has not been initialized)
     * @exception IllegalBlockSizeException if this cipher is a block cipher,
     * no padding has been requested (only in encryption mode), and the total
     * input length of the data processed by this cipher is not a multiple of
     * block size; or if this encryption algorithm is unable to
     * process the input data provided.
     * @exception ShortBufferException if the given output buffer is too small
     * to hold the result
     * @exception BadPaddingException if this cipher is in decryption mode,
     * and (un)padding has been requested, but the decrypted data is not
     * bounded by the appropriate padding bytes
     * @exception AEADBadTagException if this cipher is decrypting in an
     * AEAD mode (such as GCM/CCM), and the received authentication tag
     * does not match the calculated value
     */
    public final int doFinal(byte[] input, int inputOffset, int inputLen,
                             byte[] output)
            throws ShortBufferException, IllegalBlockSizeException,
            BadPaddingException {
        checkCipherState();

        // Input sanity check
        if (input == null || inputOffset < 0
            || inputLen > (input.length - inputOffset) || inputLen < 0) {
            throw new IllegalArgumentException("Bad arguments");
        }

        chooseFirstProvider();
//        return spi.engineDoFinal(input, inputOffset, inputLen,
//                                       output, 0);
        
        /* CRYLOGGER */
        int tmp = spi.engineDoFinal(input, inputOffset, inputLen,
                output, 0);
        CRYLogger.write("[Cipher] doFinal(byte[], int, int, output) called\n");
        CRYLogger.write("[Cipher] out bytes: " + tmp +
                " with " + transformation + "\n");
        return tmp;
    }

    /**
     * Encrypts or decrypts data in a single-part operation, or finishes a
     * multiple-part operation. The data is encrypted or decrypted,
     * depending on how this cipher was initialized.
     *
     * <p>The first <code>inputLen</code> bytes in the <code>input</code>
     * buffer, starting at <code>inputOffset</code> inclusive, and any input
     * bytes that may have been buffered during a previous
     * <code>update</code> operation, are processed, with padding
     * (if requested) being applied.
     * If an AEAD mode such as GCM/CCM is being used, the authentication
     * tag is appended in the case of encryption, or verified in the
     * case of decryption.
     * The result is stored in the <code>output</code> buffer, starting at
     * <code>outputOffset</code> inclusive.
     *
     * <p>If the <code>output</code> buffer is too small to hold the result,
     * a <code>ShortBufferException</code> is thrown. In this case, repeat this
     * call with a larger output buffer. Use
     * {@link #getOutputSize(int) getOutputSize} to determine how big
     * the output buffer should be.
     *
     * <p>Upon finishing, this method resets this cipher object to the state
     * it was in when previously initialized via a call to <code>init</code>.
     * That is, the object is reset and available to encrypt or decrypt
     * (depending on the operation mode that was specified in the call to
     * <code>init</code>) more data.
     *
     * <p>Note: if any exception is thrown, this cipher object may need to
     * be reset before it can be used again.
     *
     * <p>Note: this method should be copy-safe, which means the
     * <code>input</code> and <code>output</code> buffers can reference
     * the same byte array and no unprocessed input data is overwritten
     * when the result is copied into the output buffer.
     *
     * @param input the input buffer
     * @param inputOffset the offset in <code>input</code> where the input
     * starts
     * @param inputLen the input length
     * @param output the buffer for the result
     * @param outputOffset the offset in <code>output</code> where the result
     * is stored
     *
     * @return the number of bytes stored in <code>output</code>
     *
     * @exception IllegalStateException if this cipher is in a wrong state
     * (e.g., has not been initialized)
     * @exception IllegalBlockSizeException if this cipher is a block cipher,
     * no padding has been requested (only in encryption mode), and the total
     * input length of the data processed by this cipher is not a multiple of
     * block size; or if this encryption algorithm is unable to
     * process the input data provided.
     * @exception ShortBufferException if the given output buffer is too small
     * to hold the result
     * @exception BadPaddingException if this cipher is in decryption mode,
     * and (un)padding has been requested, but the decrypted data is not
     * bounded by the appropriate padding bytes
     * @exception AEADBadTagException if this cipher is decrypting in an
     * AEAD mode (such as GCM/CCM), and the received authentication tag
     * does not match the calculated value
     */
    public final int doFinal(byte[] input, int inputOffset, int inputLen,
                             byte[] output, int outputOffset)
            throws ShortBufferException, IllegalBlockSizeException,
            BadPaddingException {
        checkCipherState();

        // Input sanity check
        if (input == null || inputOffset < 0
            || inputLen > (input.length - inputOffset) || inputLen < 0
            || outputOffset < 0) {
            throw new IllegalArgumentException("Bad arguments");
        }

        chooseFirstProvider();
//        return spi.engineDoFinal(input, inputOffset, inputLen,
//                                       output, outputOffset);
        
        /* CRYLOGGER */
        int tmp = spi.engineDoFinal(input, inputOffset, inputLen,
                 output, outputOffset);
        CRYLogger.write("[Cipher] doFinal(byte[], int, int, byte[], int) called\n");
        CRYLogger.write("[Cipher] out bytes: " + tmp +
                " with " + transformation + "\n");
        return tmp;
    }

    /**
     * Encrypts or decrypts data in a single-part operation, or finishes a
     * multiple-part operation. The data is encrypted or decrypted,
     * depending on how this cipher was initialized.
     *
     * <p>All <code>input.remaining()</code> bytes starting at
     * <code>input.position()</code> are processed.
     * If an AEAD mode such as GCM/CCM is being used, the authentication
     * tag is appended in the case of encryption, or verified in the
     * case of decryption.
     * The result is stored in the output buffer.
     * Upon return, the input buffer's position will be equal
     * to its limit; its limit will not have changed. The output buffer's
     * position will have advanced by n, where n is the value returned
     * by this method; the output buffer's limit will not have changed.
     *
     * <p>If <code>output.remaining()</code> bytes are insufficient to
     * hold the result, a <code>ShortBufferException</code> is thrown.
     * In this case, repeat this call with a larger output buffer. Use
     * {@link #getOutputSize(int) getOutputSize} to determine how big
     * the output buffer should be.
     *
     * <p>Upon finishing, this method resets this cipher object to the state
     * it was in when previously initialized via a call to <code>init</code>.
     * That is, the object is reset and available to encrypt or decrypt
     * (depending on the operation mode that was specified in the call to
     * <code>init</code>) more data.
     *
     * <p>Note: if any exception is thrown, this cipher object may need to
     * be reset before it can be used again.
     *
     * <p>Note: this method should be copy-safe, which means the
     * <code>input</code> and <code>output</code> buffers can reference
     * the same byte array and no unprocessed input data is overwritten
     * when the result is copied into the output buffer.
     *
     * @param input the input ByteBuffer
     * @param output the output ByteBuffer
     *
     * @return the number of bytes stored in <code>output</code>
     *
     * @exception IllegalStateException if this cipher is in a wrong state
     * (e.g., has not been initialized)
     * @exception IllegalArgumentException if input and output are the
     *   same object
     * @exception ReadOnlyBufferException if the output buffer is read-only
     * @exception IllegalBlockSizeException if this cipher is a block cipher,
     * no padding has been requested (only in encryption mode), and the total
     * input length of the data processed by this cipher is not a multiple of
     * block size; or if this encryption algorithm is unable to
     * process the input data provided.
     * @exception ShortBufferException if there is insufficient space in the
     * output buffer
     * @exception BadPaddingException if this cipher is in decryption mode,
     * and (un)padding has been requested, but the decrypted data is not
     * bounded by the appropriate padding bytes
     * @exception AEADBadTagException if this cipher is decrypting in an
     * AEAD mode (such as GCM/CCM), and the received authentication tag
     * does not match the calculated value
     *
     * @since 1.5
     */
    public final int doFinal(ByteBuffer input, ByteBuffer output)
            throws ShortBufferException, IllegalBlockSizeException,
            BadPaddingException {
        checkCipherState();

        if ((input == null) || (output == null)) {
            throw new IllegalArgumentException("Buffers must not be null");
        }
        if (input == output) {
            throw new IllegalArgumentException("Input and output buffers must "
                + "not be the same object, consider using buffer.duplicate()");
        }
        if (output.isReadOnly()) {
            throw new ReadOnlyBufferException();
        }

        chooseFirstProvider();
//        return spi.engineDoFinal(input, output);
        
        /* CRYLOGGER */
        int tmp = spi.engineDoFinal(input, output);
        CRYLogger.write("[Cipher] doFinal(ByteBuffer, ByteBuffer) called\n");
        CRYLogger.write("[Cipher] out bytes: " + tmp +
                " with " + transformation + "\n");
        return tmp;
    }

    /**
     * Wrap a key.
     *
     * @param key the key to be wrapped.
     *
     * @return the wrapped key.
     *
     * @exception IllegalStateException if this cipher is in a wrong
     * state (e.g., has not been initialized).
     *
     * @exception IllegalBlockSizeException if this cipher is a block
     * cipher, no padding has been requested, and the length of the
     * encoding of the key to be wrapped is not a
     * multiple of the block size.
     *
     * @exception InvalidKeyException if it is impossible or unsafe to
     * wrap the key with this cipher (e.g., a hardware protected key is
     * being passed to a software-only cipher).
     */
    public final byte[] wrap(Key key)
            throws IllegalBlockSizeException, InvalidKeyException {
        if (!(this instanceof NullCipher)) {
            if (!initialized) {
                throw new IllegalStateException("Cipher not initialized");
            }
            if (opmode != Cipher.WRAP_MODE) {
                throw new IllegalStateException("Cipher not initialized " +
                                                "for wrapping keys");
            }
        }

        chooseFirstProvider();
        return spi.engineWrap(key);
    }

    /**
     * Unwrap a previously wrapped key.
     *
     * @param wrappedKey the key to be unwrapped.
     *
     * @param wrappedKeyAlgorithm the algorithm associated with the wrapped
     * key.
     *
     * @param wrappedKeyType the type of the wrapped key. This must be one of
     * <code>SECRET_KEY</code>, <code>PRIVATE_KEY</code>, or
     * <code>PUBLIC_KEY</code>.
     *
     * @return the unwrapped key.
     *
     * @exception IllegalStateException if this cipher is in a wrong state
     * (e.g., has not been initialized).
     *
     * @exception NoSuchAlgorithmException if no installed providers
     * can create keys of type <code>wrappedKeyType</code> for the
     * <code>wrappedKeyAlgorithm</code>.
     *
     * @exception InvalidKeyException if <code>wrappedKey</code> does not
     * represent a wrapped key of type <code>wrappedKeyType</code> for
     * the <code>wrappedKeyAlgorithm</code>.
     */
    public final Key unwrap(byte[] wrappedKey,
                            String wrappedKeyAlgorithm,
                            int wrappedKeyType)
            throws InvalidKeyException, NoSuchAlgorithmException {

        if (!(this instanceof NullCipher)) {
            if (!initialized) {
                throw new IllegalStateException("Cipher not initialized");
            }
            if (opmode != Cipher.UNWRAP_MODE) {
                throw new IllegalStateException("Cipher not initialized " +
                                                "for unwrapping keys");
            }
        }
        if ((wrappedKeyType != SECRET_KEY) &&
            (wrappedKeyType != PRIVATE_KEY) &&
            (wrappedKeyType != PUBLIC_KEY)) {
            throw new InvalidParameterException("Invalid key type");
        }

        chooseFirstProvider();
        return spi.engineUnwrap(wrappedKey,
                                      wrappedKeyAlgorithm,
                                      wrappedKeyType);
    }

    private AlgorithmParameterSpec getAlgorithmParameterSpec(
                                      AlgorithmParameters params)
            throws InvalidParameterSpecException {
        if (params == null) {
            return null;
        }

        String alg = params.getAlgorithm().toUpperCase(Locale.ENGLISH);

        if (alg.equalsIgnoreCase("RC2")) {
            return params.getParameterSpec(RC2ParameterSpec.class);
        }

        if (alg.equalsIgnoreCase("RC5")) {
            return params.getParameterSpec(RC5ParameterSpec.class);
        }

        if (alg.startsWith("PBE")) {
            return params.getParameterSpec(PBEParameterSpec.class);
        }

        if (alg.startsWith("DES")) {
            return params.getParameterSpec(IvParameterSpec.class);
        }
        return null;
    }

    private static CryptoPermission getConfiguredPermission(
            String transformation) throws NullPointerException,
            NoSuchAlgorithmException {
        if (transformation == null) throw new NullPointerException();
        String[] parts = tokenizeTransformation(transformation);
        return JceSecurityManager.INSTANCE.getCryptoPermission(parts[0]);
    }

    /**
     * Returns the maximum key length for the specified transformation
     * according to the installed JCE jurisdiction policy files. If
     * JCE unlimited strength jurisdiction policy files are installed,
     * Integer.MAX_VALUE will be returned.
     * For more information on default key size in JCE jurisdiction
     * policy files, please see Appendix E in the
     * <a href=
     *   "{@docRoot}/../technotes/guides/security/crypto/CryptoSpec.html#AppC">
     * Java Cryptography Architecture Reference Guide</a>.
     *
     * @param transformation the cipher transformation.
     * @return the maximum key length in bits or Integer.MAX_VALUE.
     * @exception NullPointerException if <code>transformation</code> is null.
     * @exception NoSuchAlgorithmException if <code>transformation</code>
     * is not a valid transformation, i.e. in the form of "algorithm" or
     * "algorithm/mode/padding".
     * @since 1.5
     */
    public static final int getMaxAllowedKeyLength(String transformation)
            throws NoSuchAlgorithmException {
        CryptoPermission cp = getConfiguredPermission(transformation);
        return cp.getMaxKeySize();
    }

    /**
     * Returns an AlgorithmParameterSpec object which contains
     * the maximum cipher parameter value according to the
     * jurisdiction policy file. If JCE unlimited strength jurisdiction
     * policy files are installed or there is no maximum limit on the
     * parameters for the specified transformation in the policy file,
     * null will be returned.
     *
     * @param transformation the cipher transformation.
     * @return an AlgorithmParameterSpec which holds the maximum
     * value or null.
     * @exception NullPointerException if <code>transformation</code>
     * is null.
     * @exception NoSuchAlgorithmException if <code>transformation</code>
     * is not a valid transformation, i.e. in the form of "algorithm" or
     * "algorithm/mode/padding".
     * @since 1.5
     */
    public static final AlgorithmParameterSpec getMaxAllowedParameterSpec(
            String transformation) throws NoSuchAlgorithmException {
        CryptoPermission cp = getConfiguredPermission(transformation);
        return cp.getAlgorithmParameterSpec();
    }

    /**
     * Continues a multi-part update of the Additional Authentication
     * Data (AAD).
     * <p>
     * Calls to this method provide AAD to the cipher when operating in
     * modes such as AEAD (GCM/CCM).  If this cipher is operating in
     * either GCM or CCM mode, all AAD must be supplied before beginning
     * operations on the ciphertext (via the {@code update} and {@code
     * doFinal} methods).
     *
     * @param src the buffer containing the Additional Authentication Data
     *
     * @throws IllegalArgumentException if the {@code src}
     * byte array is null
     * @throws IllegalStateException if this cipher is in a wrong state
     * (e.g., has not been initialized), does not accept AAD, or if
     * operating in either GCM or CCM mode and one of the {@code update}
     * methods has already been called for the active
     * encryption/decryption operation
     * @throws UnsupportedOperationException if the corresponding method
     * in the {@code CipherSpi} has not been overridden by an
     * implementation
     *
     * @since 1.7
     */
    public final void updateAAD(byte[] src) {
        if (src == null) {
            throw new IllegalArgumentException("src buffer is null");
        }

        updateAAD(src, 0, src.length);
    }

    /**
     * Continues a multi-part update of the Additional Authentication
     * Data (AAD), using a subset of the provided buffer.
     * <p>
     * Calls to this method provide AAD to the cipher when operating in
     * modes such as AEAD (GCM/CCM).  If this cipher is operating in
     * either GCM or CCM mode, all AAD must be supplied before beginning
     * operations on the ciphertext (via the {@code update} and {@code
     * doFinal} methods).
     *
     * @param src the buffer containing the AAD
     * @param offset the offset in {@code src} where the AAD input starts
     * @param len the number of AAD bytes
     *
     * @throws IllegalArgumentException if the {@code src}
     * byte array is null, or the {@code offset} or {@code length}
     * is less than 0, or the sum of the {@code offset} and
     * {@code len} is greater than the length of the
     * {@code src} byte array
     * @throws IllegalStateException if this cipher is in a wrong state
     * (e.g., has not been initialized), does not accept AAD, or if
     * operating in either GCM or CCM mode and one of the {@code update}
     * methods has already been called for the active
     * encryption/decryption operation
     * @throws UnsupportedOperationException if the corresponding method
     * in the {@code CipherSpi} has not been overridden by an
     * implementation
     *
     * @since 1.7
     */
    public final void updateAAD(byte[] src, int offset, int len) {
        checkCipherState();

        // Input sanity check
        if ((src == null) || (offset < 0) || (len < 0)
                || ((len + offset) > src.length)) {
            throw new IllegalArgumentException("Bad arguments");
        }

        chooseFirstProvider();
        if (len == 0) {
            return;
        }
        spi.engineUpdateAAD(src, offset, len);
    }

    /**
     * Continues a multi-part update of the Additional Authentication
     * Data (AAD).
     * <p>
     * Calls to this method provide AAD to the cipher when operating in
     * modes such as AEAD (GCM/CCM).  If this cipher is operating in
     * either GCM or CCM mode, all AAD must be supplied before beginning
     * operations on the ciphertext (via the {@code update} and {@code
     * doFinal} methods).
     * <p>
     * All {@code src.remaining()} bytes starting at
     * {@code src.position()} are processed.
     * Upon return, the input buffer's position will be equal
     * to its limit; its limit will not have changed.
     *
     * @param src the buffer containing the AAD
     *
     * @throws IllegalArgumentException if the {@code src ByteBuffer}
     * is null
     * @throws IllegalStateException if this cipher is in a wrong state
     * (e.g., has not been initialized), does not accept AAD, or if
     * operating in either GCM or CCM mode and one of the {@code update}
     * methods has already been called for the active
     * encryption/decryption operation
     * @throws UnsupportedOperationException if the corresponding method
     * in the {@code CipherSpi} has not been overridden by an
     * implementation
     *
     * @since 1.7
     */
    public final void updateAAD(ByteBuffer src) {
        checkCipherState();

        // Input sanity check
        if (src == null) {
            throw new IllegalArgumentException("src ByteBuffer is null");
        }

        chooseFirstProvider();
        if (src.remaining() == 0) {
            return;
        }
        spi.engineUpdateAAD(src);
    }
}