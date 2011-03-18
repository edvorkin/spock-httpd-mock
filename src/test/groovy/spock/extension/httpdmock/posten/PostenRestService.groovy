package spock.extension.httpdmock.posten

import groovy.util.slurpersupport.NodeChild
import groovyx.net.http.HTTPBuilder

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import spock.extension.httpdmock.HttpTestServer
import spock.extension.httpdmock.HttpServerCfg
import spock.extension.httpdmock.HttpServiceHandler
import spock.extension.httpdmock.HttpServiceMock
import spock.extension.httpdmock.response.XmlResponseWriter
import spock.lang.Specification

/**
 * Testing the service handler mechanism by implementing a service mock
 * for a simple rest service provided by the Norwegian postal service. 
 * @author Kim A. Betti
 */
class PostenRestServiceSpec extends Specification {
  
    @HttpServerCfg
    HttpTestServer server
    
    @HttpServiceMock(PostenRestServiceHandler)
    ZipCodeResolver postenZipResolver = Mock()

    def "Should translate http requests into method invokations on the service interface"() {
        given: "a http client"
        HTTPBuilder http = new HTTPBuilder(server.baseUri)
        
        when: "we execute a simple http get request to the zip code resolver endpoint"
        NodeChild xmlResponse = http.get(path: "/fraktguide/postalCode.xml", query: [ pnr: "9710" ])

        then: "the http request is translated into a method invokation on the interface mock"
        1 * postenZipResolver.lookup("9710") >> "INDRE BILLEFJORD"
        
        and: "the service handler has translated our String into an xml response on the expected format"
        xmlResponse.Response.text() == "INDRE BILLEFJORD"
    }
    
    def "Testing how the endpoint deals with invalid zip codes"() {
        given: "a http client"
        HTTPBuilder http = new HTTPBuilder(server.baseUri)
        
        when: "we request the city name for an invalid zip code"
        NodeChild xmlResponse = http.get(path: "/fraktguide/postalCode.xml", query: [ pnr: "xxx" ])
        
        then: "the service handler interpreters the mocks null response as an invalid zip code"
        xmlResponse.Response."@valid" == false
        xmlResponse.Response.text() == "Ugyldig postnummer"
    }

}

/**
 * Encapsulation of the Norwegian Postal Service REST service for looking up zip codes.
 * @see http://fraktguide.bring.no/fraktguide/postalCode.xml?pnr=7600
 * @author Kim A. Betti
 */
class PostenRestServiceHandler implements HttpServiceHandler {

    ZipCodeResolver mock

    boolean canHandle(String target, HttpServletRequest request) {
        target.startsWith "/fraktguide/postalCode.xml"
    }

    void handleRequest(String target, HttpServletRequest request, HttpServletResponse response) {
        String zipCode = request.parameterMap["pnr"][0]
        String cityName = mock.lookup(zipCode)

        XmlResponseWriter.build(response) {
            PostalCodeQueryResponse {
                Response(valid: cityName != null, cityName ?: "Ugyldig postnummer")
            }
        } 
    }
    
}

/**
 * This interface captures the essence, or contract if you will
 * of the http service end point without bothering with how 
 * the actual http requests look like. 
 * @author Kim A. Betti
 */
interface ZipCodeResolver {
    
    String lookup(String zipCode);
    
}