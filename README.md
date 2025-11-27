NOTE: in src\main\resources\templates\fragments\
there is exist an `final.html` file 
since it's 3mb 66k lines and 4 millions characters svg, `final.html` files has been excluded from commited to git.

```html
<svg th:fragment="final (w, h)" version="1.1" viewBox="0 0 1448 1504" xmlns="http://www.w3.org/2000/svg"
    preserveAspectRatio="xMidYMid meet" 
    th:style="'width:' + ${w} + '; height:' + ${h} + ';
     display:block;'">

 <!-- 66k lines 4 million characters later  -->

</svg>
```