# Data-Structure_Final-Project
Proyecto Final de Estructura de Datos con el docente Juan Carlos García Ojeda - Universidad de Cartagena, Colombia

Se realizó una interfaz gráfica en el IDE *__"Java Netbeans"__* para resolver problemas de procesos de flujos PNS basados en P-Graph integrando Solvers comerciales y de open source mediante la optimización de resultados con técnicas de modelado MILP y algoritmos Branch And Bound.

Para la construcción del proyecto, se distribuyó en cuatro (4) capas/carpetas su funcionamiento:
    - Interfaz     ==> contiene el main y las visuales
    - Modelo       ==> modelado en java basado en: materiales, unidades de proceso y tasas de flujo.
    - Optimización ==> contiene las clases de Optimizacion de cada solver y la clase que almacena el resultado.
    - Persistencia ==> contiene la lectura del modelo P-Graph en .txt, y guardado de soluciones en .JSON, así como el manejo de errores.

Los solvers utilizados fueron:
    - Gurobi                      ==> Descarga del software/kit, adquisición licencia acádemica y archivo .JAR
    - Apache Commons Math 4 core  ==> Descarga de archivo(s) .JAR
    - CPLEX                       ==> Descarga del software/Kit y archivo .JAR
    - GLPK                        ==> Descarga del software/kit y archivo .JAR

Y, para la configuración de la interfaz gráfica, visuales, analisis y comparación de resultados se utilizó *__JavaFX__*. Para ello se debió adquirir los archivos .bin y .JAR desde la página de Oracle JavaFX, descargando el .zip.

Los solvers y JavaFX fueron integrados mediante sus correspondientes Java APIs (librerías en formato .JAR) en el apartado *__Classpath__* de librerias y para el apartado *__Modulepath__* se requirió insertar de igual manera tres (3) librerias de JavaFX, agregadas ya en sus respectivas carpetas dentro del proyecto.

Java necesita los archivos binarios tanto de los solvers como de JavaFX para poder ejecutar correctamente, por ende, para los archivos de programa donde se empaqueta cada uno se recomiendan, en caso de no seguir las rutas de instalación por defecto, ser trasladados a la ruta del disco duro "C:\" para mayor facilidad al momento de ser buscados por el IDE.

__EJEMPLO:__
C:\javafx-sdk-26.0.1 ==> __ruta de los archivos trasladada__
C:\gurobi1302        ==> ruta por defecto de Gurobi
C:\Program Files\IBM ==> ruta por defecto de CPLEX
C:\glpk-4.65         ==> ruta por defecto de GLPK

Para el caso de Apache Commons Math, no necesita estas rutas ya que no utilizan binarios, basta con solo insertar sus archivos .JAR en Java Netbeans: 
__ruta "creada" recomendada donde ubiques todos tus archivos .JAR para la "classpath" y "modulepath"__
1) C:\Users\USER\Documents\NetBeansProjects\FinalProject_ANT\lib\classpath
2) C:\Users\USER\Documents\NetBeansProjects\FinalProject_ANT\lib\modulepath

Finalmente, como Java necesita saber la ruta de ubicación de los binarios, se insertó el siguiente comando dentro de las configuraciones/propiedades del proyecto propio de Java

Hacer click derecho en el nombre del proyecto y seguir la ruta:
Properties --> Run --> VMOptions: -->

--enable-native-access=ALL-UNNAMED --add-modules javafx.controls,javafx.graphics,javafx.base -Djava.library.path="C:\gurobi1302\win64\bin;C:\glpk-4.65\w64;C:\Program Files\IBM\ILOG\CPLEX_Studio_Community222\cplex\bin\x64_win64;C:\javafx-sdk-26.0.1\bin" -Dfile.encoding=UTF-8

Con esto, el proyecto ya esta configurado para funcionar.
