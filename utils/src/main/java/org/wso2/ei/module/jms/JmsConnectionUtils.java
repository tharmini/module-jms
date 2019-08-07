package org.wso2.ei.module.jms;

import org.ballerinalang.jvm.values.MapValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.naming.InitialContext;
import javax.naming.NamingException;

public class JmsConnectionUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(JmsConnectionUtils.class);

    private JmsConnectionUtils() {
    }

    public static Connection createJmsConnection(String initialContextFactory, String providerUrl,
                                                 String connectionFactoryName ,
                                                 MapValue<String, String> optionalConfigs) throws BallerinaJmsException {

        
        Connection connection = createConnection(initialContextFactory, providerUrl, connectionFactoryName,
                                                          optionalConfigs);
        try {
            if (connection.getClientID() == null) {
                connection.setClientID(UUID.randomUUID().toString());
            }
            connection.setExceptionListener(new LoggingExceptionListener());
            connection.start();
        } catch (JMSException e) {
            throw new BallerinaJmsException("Error occurred while starting connection.", e);
        }
        return connection;
    }

    public static void startJmsConnection(Connection connection) throws BallerinaJmsException {
        try {
            connection.start();
        } catch (JMSException e) {
            throw new BallerinaJmsException("Error starting connection", e);
        }
    }

    public static void stopJmsConnection(Connection connection) throws BallerinaJmsException {
        try {
            connection.stop();
        } catch (JMSException e) {
            throw new BallerinaJmsException("Error stopping connection", e);
        }
    }

    private static Connection createConnection(String initialContextFactory, String providerUrl,
                                              String connectionFactoryName ,
                                              MapValue<String, String> optionalConfigs) throws BallerinaJmsException{
        Map<String, String> configParams = new HashMap<>();

        configParams.put(JmsConstants.ALIAS_INITIAL_CONTEXT_FACTORY, initialContextFactory);

        configParams.put(JmsConstants.ALIAS_PROVIDER_URL, providerUrl);

        configParams.put(JmsConstants.ALIAS_CONNECTION_FACTORY_NAME, connectionFactoryName);

        preProcessIfWso2MB(configParams);
        updateMappedParameters(configParams);

        Properties properties = new Properties();
        configParams.forEach(properties::put);

        optionalConfigs.forEach(properties::put);

        try {
            InitialContext initialContext = new InitialContext(properties);
            ConnectionFactory connectionFactory = (ConnectionFactory) initialContext.lookup(connectionFactoryName);
            String username = optionalConfigs.getStringValue(JmsConstants.ALIAS_USERNAME);
            String password = optionalConfigs.getStringValue(JmsConstants.ALIAS_PASSWORD);

            if (!JmsUtils.isNullOrEmptyAfterTrim(username) && password != null) {
                return connectionFactory.createConnection(username, password);
            } else {
                return connectionFactory.createConnection();
            }
        } catch (NamingException | JMSException e) {
            String message = "Error while connecting to broker.";
            LOGGER.error(message, e);
            throw new BallerinaJmsException(message + " " + e.getMessage(), e);
        }
    }


    //
    private static void preProcessIfWso2MB(Map<String, String> configParams) throws BallerinaJmsException {
        String initialConnectionFactoryName = configParams.get(JmsConstants.ALIAS_INITIAL_CONTEXT_FACTORY);
        if (JmsConstants.BMB_ICF_ALIAS.equalsIgnoreCase(initialConnectionFactoryName)
            || JmsConstants.MB_ICF_ALIAS.equalsIgnoreCase(initialConnectionFactoryName)) {

            configParams.put(JmsConstants.ALIAS_INITIAL_CONTEXT_FACTORY, JmsConstants.MB_ICF_NAME);
            String connectionFactoryName = configParams.get(JmsConstants.ALIAS_CONNECTION_FACTORY_NAME);
            if (configParams.get(JmsConstants.ALIAS_PROVIDER_URL) != null) {
                System.setProperty("qpid.dest_syntax", "BURL");
                if (!JmsUtils.isNullOrEmptyAfterTrim(connectionFactoryName)) {
                    configParams.put(JmsConstants.MB_CF_NAME_PREFIX + connectionFactoryName,
                                     configParams.get(JmsConstants.ALIAS_PROVIDER_URL));
                    configParams.remove(JmsConstants.ALIAS_PROVIDER_URL);
                } else {
                    throw new BallerinaJmsException(
                            JmsConstants.ALIAS_CONNECTION_FACTORY_NAME + " property should be set");
                }
            } else if (configParams.get(JmsConstants.CONFIG_FILE_PATH) != null) {
                configParams.put(JmsConstants.ALIAS_PROVIDER_URL, configParams.get(JmsConstants.CONFIG_FILE_PATH));
                configParams.remove(JmsConstants.CONFIG_FILE_PATH);
            }
        }
    }

    private static void updateMappedParameters(Map<String, String> configParams) {
        Iterator<Map.Entry<String, String>> iterator = configParams.entrySet().iterator();
        Map<String, String> tempMap = new HashMap<>();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            String mappedParam = JmsConstants.MAPPING_PARAMETERS.get(entry.getKey());
            if (mappedParam != null) {
                tempMap.put(mappedParam, entry.getValue());
                iterator.remove();
            }
        }
        configParams.putAll(tempMap);
    }
}