package mini_java;
//导入map
//导入list
//导入stack
//导入hashmap
//导入arraylist






class Typing {

  static boolean debug = false;

  // use this method to signal typing errors
  static void error(Location loc, String msg) {
    String l = loc == null ? " <no location>" : " " + loc;
    throw new Error(l + "\nerror: " + msg);

  }


  static TFile file(PFile f) {
    TransferPT pt=new TransferPT(f);
    TFile Tf=pt.getResult();

    return Tf;
  }

}
