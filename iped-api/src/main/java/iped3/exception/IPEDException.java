/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iped3.exception;

/**
 *
 * @author WERNECK
 */
public class IPEDException extends Exception {

	public IPEDException(Throwable e) {
		super(e);
	}

	public IPEDException(String string) {
		super(string);
	}

}
