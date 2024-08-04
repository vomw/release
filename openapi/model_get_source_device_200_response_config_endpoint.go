/*
 * untitled API
 *
 * No description provided (generated by Openapi Generator https://github.com/openapitools/openapi-generator)
 *
 * API version: 536
 */

// Code generated by OpenAPI Generator (https://openapi-generator.tech); DO NOT EDIT.

package openapi

import (
	"encoding/json"
)

// GetSourceDevice200ResponseConfigEndpoint struct for GetSourceDevice200ResponseConfigEndpoint
type GetSourceDevice200ResponseConfigEndpoint struct {
	Host string `json:"host"`
	V4 string `json:"v4"`
	V6 string `json:"v6"`
}

// NewGetSourceDevice200ResponseConfigEndpoint instantiates a new GetSourceDevice200ResponseConfigEndpoint object
// This constructor will assign default values to properties that have it defined,
// and makes sure properties required by API are set, but the set of arguments
// will change when the set of required properties is changed
func NewGetSourceDevice200ResponseConfigEndpoint(host string, v4 string, v6 string, ) *GetSourceDevice200ResponseConfigEndpoint {
	this := GetSourceDevice200ResponseConfigEndpoint{}
	this.Host = host
	this.V4 = v4
	this.V6 = v6
	return &this
}

// NewGetSourceDevice200ResponseConfigEndpointWithDefaults instantiates a new GetSourceDevice200ResponseConfigEndpoint object
// This constructor will only assign default values to properties that have it defined,
// but it doesn't guarantee that properties required by API are set
func NewGetSourceDevice200ResponseConfigEndpointWithDefaults() *GetSourceDevice200ResponseConfigEndpoint {
	this := GetSourceDevice200ResponseConfigEndpoint{}
	return &this
}

// GetHost returns the Host field value
func (o *GetSourceDevice200ResponseConfigEndpoint) GetHost() string {
	if o == nil  {
		var ret string
		return ret
	}

	return o.Host
}

// GetHostOk returns a tuple with the Host field value
// and a boolean to check if the value has been set.
func (o *GetSourceDevice200ResponseConfigEndpoint) GetHostOk() (*string, bool) {
	if o == nil  {
		return nil, false
	}
	return &o.Host, true
}

// SetHost sets field value
func (o *GetSourceDevice200ResponseConfigEndpoint) SetHost(v string) {
	o.Host = v
}

// GetV4 returns the V4 field value
func (o *GetSourceDevice200ResponseConfigEndpoint) GetV4() string {
	if o == nil  {
		var ret string
		return ret
	}

	return o.V4
}

// GetV4Ok returns a tuple with the V4 field value
// and a boolean to check if the value has been set.
func (o *GetSourceDevice200ResponseConfigEndpoint) GetV4Ok() (*string, bool) {
	if o == nil  {
		return nil, false
	}
	return &o.V4, true
}

// SetV4 sets field value
func (o *GetSourceDevice200ResponseConfigEndpoint) SetV4(v string) {
	o.V4 = v
}

// GetV6 returns the V6 field value
func (o *GetSourceDevice200ResponseConfigEndpoint) GetV6() string {
	if o == nil  {
		var ret string
		return ret
	}

	return o.V6
}

// GetV6Ok returns a tuple with the V6 field value
// and a boolean to check if the value has been set.
func (o *GetSourceDevice200ResponseConfigEndpoint) GetV6Ok() (*string, bool) {
	if o == nil  {
		return nil, false
	}
	return &o.V6, true
}

// SetV6 sets field value
func (o *GetSourceDevice200ResponseConfigEndpoint) SetV6(v string) {
	o.V6 = v
}

func (o GetSourceDevice200ResponseConfigEndpoint) MarshalJSON() ([]byte, error) {
	toSerialize := map[string]interface{}{}
	if true {
		toSerialize["host"] = o.Host
	}
	if true {
		toSerialize["v4"] = o.V4
	}
	if true {
		toSerialize["v6"] = o.V6
	}
	return json.Marshal(toSerialize)
}

type NullableGetSourceDevice200ResponseConfigEndpoint struct {
	value *GetSourceDevice200ResponseConfigEndpoint
	isSet bool
}

func (v NullableGetSourceDevice200ResponseConfigEndpoint) Get() *GetSourceDevice200ResponseConfigEndpoint {
	return v.value
}

func (v *NullableGetSourceDevice200ResponseConfigEndpoint) Set(val *GetSourceDevice200ResponseConfigEndpoint) {
	v.value = val
	v.isSet = true
}

func (v NullableGetSourceDevice200ResponseConfigEndpoint) IsSet() bool {
	return v.isSet
}

func (v *NullableGetSourceDevice200ResponseConfigEndpoint) Unset() {
	v.value = nil
	v.isSet = false
}

func NewNullableGetSourceDevice200ResponseConfigEndpoint(val *GetSourceDevice200ResponseConfigEndpoint) *NullableGetSourceDevice200ResponseConfigEndpoint {
	return &NullableGetSourceDevice200ResponseConfigEndpoint{value: val, isSet: true}
}

func (v NullableGetSourceDevice200ResponseConfigEndpoint) MarshalJSON() ([]byte, error) {
	return json.Marshal(v.value)
}

func (v *NullableGetSourceDevice200ResponseConfigEndpoint) UnmarshalJSON(src []byte) error {
	v.isSet = true
	return json.Unmarshal(src, &v.value)
}


