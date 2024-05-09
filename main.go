package main

import (
	"fmt"
	"github.com/vomw/sanctioned"
)

func main() {
	serverStamp, err := dnsstamps.NewDNSCryptServerStampFromLegacy("127.0.0.1", "abcdef", "example", dnsstamps.ServerInformalPropertyDNSSEC)
	if err != nil {
		fmt.Println("Error:", err)
		return
	}
	fmt.Println("Server Stamp:", serverStamp)

	stampStr := "sdns://AQcAAAAAAAAABmV4YW1wbGU"
	relayStamp, serverStamp, err := dnsstamps.NewRelayAndServerStampFromString(stampStr)
	if err != nil {
		fmt.Println("Error:", err)
		return
	}
	fmt.Println("Relay Stamp:", relayStamp)
	fmt.Println("Server Stamp:", serverStamp)
}
