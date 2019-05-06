/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package macee.descriptor;

/**
 *
 * @author WERNECK
 */
public interface UserDescriptor extends Descriptor {

  String getEmail();
  //    public static void main(String[] args) {
  //        UserDescriptor ud = new UserDescriptor("test user", "test@user.com");
  //        System.out.println(ud.toJson());
  //        String json = ud.toJson();
  //
  //        DefaultDescriptor abs = (DefaultDescriptor) ud;
  //        System.out.println(abs.toJson());
  //
  //        abs = DefaultDescriptor.gson.fromJson(json, DefaultDescriptor.class);
  //        System.out.println(abs.toJson());
  //    }
  
}
