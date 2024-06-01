// Start standard library for non-native js functions
const argv = []
const __std = Object.freeze({
    arr: (...els) => els,
    pusharr: (arr, el) => arr.push(el),
    poparr: (arr) => arr.pop(el),
    assignArrIdx: (arr, idx, el) => arr[idx] = el,
    exit: (code) => {
        throw `Program exited with code ${code}`
    },
    not_supported_js: () => {
        throw "Some function wasn't able to be transpiled to JavaScript :("
    },
    join_obj: (obj, sep) => {
		return obj.join(sep);
	},
});
// End standard library for non-native js functions
               
                
console.log("Maagun")
function order(items) {
	const itemsJoined = __std.join_obj(items, ",");
	console.log(itemsJoined)
	return fetch("http://localhost:3000/order?body=" + itemsJoined)
}