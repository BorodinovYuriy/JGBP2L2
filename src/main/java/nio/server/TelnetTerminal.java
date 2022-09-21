package nio.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

public class TelnetTerminal {

    /**
     * Support commands:
     * cd path - go to dir
     * touch filename - create file with filename
     * mkdir dirname - create directory with dirname
     * cat filename - show filename bytes
     *
     * cd
     * touch
     * mkdir
     * cat
     * */


    private Path current;
    private ServerSocketChannel server;
    private Selector selector;

    private ByteBuffer buf;

        public TelnetTerminal() throws IOException {
        //создание папки сервера
        current = Path.of("server_files");
        File file = current.toFile();
        if (!file.exists()) {
            file.mkdir();
        }

        buf = ByteBuffer.allocate(256);
        server = ServerSocketChannel.open();
        //не через new а через фабр метод, так как провайдеры для разных операционок отличаются
        selector = Selector.open();
        server.bind(new InetSocketAddress(8189));
        server.configureBlocking(false);
        //сервер умеет только выдавать соединение
        server.register(selector, SelectionKey.OP_ACCEPT);

        while (server.isOpen()) {
            //Прослушка нажатых клавиш или хз
            selector.select();
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = keys.iterator();

            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                if (key.isAcceptable()) {
                    handleAccept();
                }
                if (key.isReadable()) {
                    handleRead(key);
                }
                keyIterator.remove();
            }
        }
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        buf.clear();
        StringBuilder sb = new StringBuilder();
        while (true) {
            int read = channel.read(buf);
            if (read == 0) {
                break;
            }
            if (read == -1) {
                channel.close();
                return;
            }
            buf.flip();
            while (buf.hasRemaining()) {
                sb.append((char) buf.get());
            }
            buf.clear();
        }
        System.out.println("Received: " + sb);
        String command = sb.toString().trim();
        commandExecute(command, channel);

        }

    private void commandExecute(String command, SocketChannel channel) {
            try{
                        if(command.startsWith("ls")){
                            channel.write(ByteBuffer.wrap("Список файлов и папок: \n".getBytes()));
                            String files = Files.list(current)
                                    .map(p -> p.getFileName().toString())
                                    .collect(Collectors.joining("\n\r"));
                            channel.write(ByteBuffer.wrap(files.getBytes(StandardCharsets.UTF_8)));
                            channel.write(ByteBuffer.wrap("\n".getBytes()));

                        } else if(command.startsWith("mkdir")){
                            String inDirName = command.substring(5).trim();
                            /*Path filePath = Path.of(current + "/" + inDirName);*/
                            Path filePath = current.resolve(inDirName);
                            //умеет создать папку только в current...
                            System.out.println(filePath);
                            File file = filePath.toFile();
                            if (!file.exists()) {
                                file.mkdir();
                            }

                        } else if (command.startsWith("touch")) {
                            String inFileName = command.substring(5).trim();
                            Path filePath = current.resolve(inFileName);
                            System.out.println(filePath);
                            File file = filePath.toFile();
                            if (!file.exists()) {
                                file.createNewFile();
                            }

                        } else if (command.startsWith("cat")) {
                                String catFile = command.substring(3).trim();
                                Path theWayToCat = current.resolve(catFile).normalize();
                                File f = theWayToCat.toFile();
                            if (f.isFile()){
                                System.out.println("ok");
                                try(FileInputStream fis = new FileInputStream(f)) {
                                    channel.write(ByteBuffer.wrap(fis.readAllBytes()));
                                    channel.write(ByteBuffer.wrap("\n".getBytes()));
                                }
                            }

                        } else if (command.startsWith("cd")) {
                            String inOpenDirName = command.substring(2).trim();
                            System.out.println("input dir is: "+inOpenDirName);
                            try{
                                Path theWay = current.resolve(inOpenDirName).normalize();
                                System.out.println(theWay+"   !!!!");
                                if (theWay.toFile().isDirectory()){
                                    current = current.resolve(inOpenDirName).normalize();
                                }

                            }catch (NullPointerException e){
                                current = Path.of("server_files");
                                channel.write(ByteBuffer.wrap(("Wrong way!!!").getBytes()));
                                channel.write(ByteBuffer.wrap("\n".getBytes()));
                            }

                            channel.write(ByteBuffer.wrap(("You are here : " + current).getBytes()));
                            channel.write(ByteBuffer.wrap("\n".getBytes()));

                        } else {
                            byte[] bytes = command.getBytes(StandardCharsets.UTF_8);
                            channel.write(ByteBuffer.wrap("Unknowing command : ".getBytes()));
                            channel.write(ByteBuffer.wrap(bytes));
                            channel.write(ByteBuffer.wrap("\n".getBytes()));
                        }

            }catch (IOException e) {
                e.printStackTrace();
            }
    }

    private void handleAccept() throws IOException {
        SocketChannel socketChannel = server.accept();
        socketChannel.configureBlocking(false);
        socketChannel.register(selector, SelectionKey.OP_READ);
        System.out.println("Client accepted");
    }

    public static void main(String[] args) throws IOException {
        new TelnetTerminal();
    }
}
