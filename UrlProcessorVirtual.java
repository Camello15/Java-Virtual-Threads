import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrlProcessorVirtual {

    // Ruta del archivo de entrada con URLs
    private static final String INPUT = "D:\\Users\\Smart\\Downloads\\url\\urls_parcial1-1.txt";

    // Ruta del archivo donde se guardarán los resultados
    private static final String OUTPUT = "D:\\Users\\Smart\\Downloads\\url\\resultados.txt";

    // Expresión regular para buscar enlaces href que sean URLs HTTP o HTTPS
    private static final Pattern LINK_PATTERN = Pattern.compile(
            "href\\s*=\\s*\"(http[s]?://[^\"]+)\"", Pattern.CASE_INSENSITIVE);

    public static void main(String[] args) throws Exception {
        /*
         * Cambio importante:
         * Antes usábamos ExecutorService con un pool de hilos tradicionales fijos o variables,
         * pero ahora usamos un ExecutorService que crea un hilo virtual por cada tarea.
         * Esto mejora la eficiencia y escalabilidad porque los hilos virtuales son muy livianos
         * y permiten crear miles sin gran consumo de recursos.
         */
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        // Leer todas las URLs desde el archivo de entrada
        List<String> urls = Files.readAllLines(Paths.get(INPUT));

        // Para cada URL, crear una tarea que procesa la URL y la envía al executor (con hilos virtuales)
        List<Future<String>> tasks = urls.stream()
                .map(url -> executor.submit(() -> processUrl(url)))  // Aquí cada tarea corre en un hilo virtual
                .toList();

        // Abrir el archivo de salida para escribir los resultados
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(OUTPUT))) {
            // Para cada tarea, esperar el resultado (bloquea hasta que termine) y escribirlo en el archivo
            for (Future<String> task : tasks) {
                try {
                    writer.write(task.get());
                    writer.newLine();
                } catch (Exception e) {
                    // En caso de error en la tarea, escribir mensaje de error
                    writer.write("ERROR: " + e.getMessage());
                    writer.newLine();
                }
            }
        }

        // Apagar el executor para liberar recursos
        executor.shutdown();
    }

    // Método que procesa una URL: hace petición HTTP, cuenta enlaces internos y devuelve el resultado
    private static String processUrl(String urlStr) {
        try {
            // Crear cliente HTTP
            HttpClient client = HttpClient.newHttpClient();

            // Construir la petición HTTP GET con timeout de 10 segundos
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlStr))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .GET()
                    .build();

            // Enviar la petición y obtener la respuesta como String
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String html = response.body();

            // Extraer el dominio base de la URL para filtrar enlaces internos
            URI baseUri = URI.create(urlStr);
            String domain = baseUri.getHost();
            int count = 0;

            // Buscar con regex todos los enlaces href en el HTML
            Matcher matcher = LINK_PATTERN.matcher(html);
            while (matcher.find()) {
                String link = matcher.group(1);
                try {
                    URI linkUri = URI.create(link);
                    // Contar solo enlaces que tengan el mismo dominio que la URL base (enlaces internos)
                    if (linkUri.getHost() != null && linkUri.getHost().endsWith(domain)) {
                        count++;
                    }
                } catch (Exception ignored) {
                    // Ignorar enlaces mal formados
                }
            }

            // Devolver un resumen con la URL y la cantidad de enlaces internos encontrados
            return urlStr + " --> " + count + " enlaces internos";
        } catch (Exception e) {
            // En caso de error al procesar la URL, devolver mensaje con error
            return urlStr + " --> ERROR: " + e.getMessage();
        }
    }
}
