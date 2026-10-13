//step 1 can do on server powershell or differnce one
mkdir out, files
javac -d out Server.java Client.java

//step 2 
Remove-Item files\test.bin -ErrorAction SilentlyContinue
$r = New-Object Random; $b = New-Object byte[] 1048576; $f = [IO.File]::Create("$PWD\files\test.bin"); 1..1024 | ForEach-Object { $r.NextBytes($b); $f.Write($b,0,$b.Length) }; $f.Close()

//step 3
(Get-Item files\test.bin).Length

//step 4
certutil -hashfile files\test.bin SHA256

//step 5 server powershell
java -cp out Server 5000 files nio

//step 6 client powershell
java -cp out Client localhost 5000 LIST

//step 7 client for testing 
function Send-Cmd($c) {
  $t = New-Object Net.Sockets.TcpClient("localhost", 5000)
  $s = $t.GetStream()
  $w = New-Object IO.StreamWriter($s)
  $w.NewLine = "`n"
  $w.WriteLine($c); $w.Flush()
  $r = New-Object IO.StreamReader($s)
  $r.ReadLine()
  $t.Close()
}
Send-Cmd "INFO test.bin"
Send-Cmd "INFO ../etc/passwd"
Send-Cmd "GET test.bin 0 99999999999"

//step 8 client for download 10 worker and verify connect
java -cp out Client localhost 5000 test.bin nio 10 out.bin
certutil -hashfile out.bin SHA256
//step 9 compare 1 vs 10 worker 
java -cp out Client localhost 5000 test.bin nio 1 out.bin
java -cp out Client localhost 5000 test.bin nio 10 out.bin

//step 10 compare traditional I/O
//server 
   java -cp out Server 5000 files trad
//client 
   java -cp out Client localhost 5000 test.bin trad 1 out.bin
   java -cp out Client localhost 5000 test.bin trad 10 out.bin
