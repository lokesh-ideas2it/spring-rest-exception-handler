/*
 * Copyright 2014 Jakub Jirutka <jakub@jirutka.cz>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cz.jirutka.spring.exhandler

import groovy.json.JsonSlurper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import spock.lang.Issue
import spock.lang.Specification

import static org.springframework.http.MediaType.APPLICATION_XML
import static org.springframework.http.MediaType.TEXT_PLAIN
import static org.springframework.http.MediaType.APPLICATION_JSON

@WebAppConfiguration
abstract class AbstractConfigurationIT extends Specification {

    static final JSON_UTF8 = 'application/json;charset=UTF-8'

    static {
        MockHttpServletResponse.metaClass.getContentAsJson = {
            new JsonSlurper().parseText(delegate.contentAsString)
        }
        MockHttpServletResponse.metaClass.getContentAsXml = {
            new XmlSlurper().parseText(delegate.contentAsString)
        }
    }

    @Autowired WebApplicationContext context

    MockMvc mockMvc
    MockHttpServletResponse response

    static GET = MockMvcRequestBuilders.&get
    static POST = MockMvcRequestBuilders.&post

    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build()
    }


    def 'Perform request that results in success response'() {
        when:
            perform GET('/ping').with {
                accept TEXT_PLAIN
            }
        then: 'no error here'
            response.status == 200
    }

    def 'Perform request that causes built-in exception handled by default handler'() {

        when: 'require content type not supported by the resource'
            perform GET('/ping').with {
                accept APPLICATION_JSON
            }

        then: 'we got Not Acceptable error'
            response.status == 406

        and: 'Content-Type corresponds to the requested type'
            response.contentType == JSON_UTF8

        and: 'body contains expected message in correct type'
            with (response.contentAsJson) {
                type   == 'http://httpstatus.es/406'
                title  == 'This sucks!'
                status == 406
                detail == "This resource provides only text/plain, but you've sent request with Accept application/json."
            }
    }

    def 'Perform request that causes user-defined exception with custom exception handler'() {

        when: 'perform request on resource that throws ZuulException'
            perform GET('/dana').with {
                accept APPLICATION_JSON
            }

        then: 'we got error response with status specified in ZuulExceptionHandler'
            response.status == 404

        and: 'Content-Type corresponds to the requested type'
            response.contentType == JSON_UTF8

        and: 'body is generated by ZuulExceptionHandler in correct type'
            response.contentAsJson.title == "There's no Dana, only Zuul!"
    }

    def 'Perform request with Accept different from defaultContentType'() {

        when: 'perform request on resource that throws ZuulException and require non-default content type'
            perform GET('/dana').with {
                accept APPLICATION_XML
            }

        then: 'we got error response with status specified in ZuulExceptionHandler'
            response.status == 404

        and: 'Content-Type corresponds to the requested type'
            response.contentType == 'application/xml'

        and: 'body is generated by ZuulExceptionHandler in correct type'
            response.contentAsXml.title == "There's no Dana, only Zuul!"
    }

    @Issue('#2')
    def 'Perform request without Accept that causes handled exception'() {

        when: "perform request on resource that throws ZuulException and don't specify Accept"
            perform GET('/dana')

        then: 'we get error response with the status specified in ZuulExceptionHandler'
            response.status == 404

        and: 'Content-Type corresponds to the configured defaultContentType'
            response.contentType == JSON_UTF8
            response.contentAsJson.title == "There's no Dana, only Zuul!"
    }

    def 'Perform request with Accept that is not supported by resource and exception handler'() {
        when:
            perform GET('/ping').with {
                accept MediaType.valueOf('image/png')
            }
        then: 'we got Not Acceptable error'
            response.status == 406

        and: 'Content-Type corresponds to the configured default (fallback) type'
            response.contentType == JSON_UTF8
    }

    def 'Perform request that causes built-in exception handled by default handler remapped to different status'() {

        when: 'use method not supported by the resource'
            perform POST('/ping')

        then: 'we got 418 instead of 405 that is default for this error'
            response.status == 418
        and:
            response.contentType == JSON_UTF8
            response.contentAsJson.title == 'Method Not Allowed'
    }

    def 'Perform request that causes user-defined exception handled by @ExceptionHandler method'() {

        when: 'perform request on resource that throws exception'
            perform POST('/teapot').with {
                accept APPLICATION_JSON
            }

        then: 'we got error response with status specified in @ResponseStatus on @ExceptionHandler method'
            response.status      == 418

        and: 'body is generated by @ExceptionHandler method'
            response.contentType == JSON_UTF8
            response.contentAsJson.title == 'Bazinga!'
    }


    def perform(builder) {
        response = mockMvc.perform(builder).andReturn().response
    }

    def parseJson(string) {
        new JsonSlurper().parseText(string)
    }
}
